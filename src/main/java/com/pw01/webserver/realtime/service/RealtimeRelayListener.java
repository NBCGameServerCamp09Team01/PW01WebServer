package com.pw01.webserver.realtime.service;

import com.pw01.webserver.config.RealtimeRelayProperties;
import com.pw01.webserver.realtime.dto.RealtimeRelayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 다른 서버가 Redis 채널에 올린 메시지를 받아 이 서버의 연결에 보낸다(루트 docs/contracts/redis-keys.md "실시간 서버 사이 전달").
 * RealtimeRelayConfig의 구독 컨테이너가 받기 스레드 하나로 부른다(한 서버에서 온 메시지 순서를 지킨다).
 * 이상한 메시지(깨진 JSON·모르는 판·모르는 종류·한도 초과·채널과 길드가 다름)는 버리고 경고 로그만 남긴다. 구독은 계속된다.
 */
@Service
public class RealtimeRelayListener implements MessageListener, SubscriptionListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRelayListener.class);

    private final RealtimeLocalDelivery delivery;
    private final RealtimeConnectionRegistry registry;
    private final RealtimeHeartbeat heartbeat;
    private final RealtimeRelayProperties properties;
    private final RealtimeServerId serverId;
    /** 앱 전체는 "모르는 칸이면 실패"다. 서버 사이 메시지는 새 서버가 칸을 더해도 옛 서버가 깨지지 않게 모르는 칸을 무시한다 */
    private final ObjectReader reader;

    public RealtimeRelayListener(RealtimeLocalDelivery delivery, RealtimeConnectionRegistry registry,
                                 RealtimeHeartbeat heartbeat, RealtimeRelayProperties properties,
                                 RealtimeServerId serverId, JsonMapper jsonMapper) {
        this.delivery = delivery;
        this.registry = registry;
        this.heartbeat = heartbeat;
        this.properties = properties;
        this.serverId = serverId;
        this.reader = jsonMapper.readerFor(RealtimeRelayMessage.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        try {
            handle(channel, message.getBody());
        } catch (RuntimeException e) {
            // 받기 스레드가 죽지 않게 모두 잡는다
            log.warn("[Relay] dropped: could not handle channel={}", channel, e);
        }
    }

    void handle(String channel, byte[] body) {
        RealtimeRelayMessage relayMessage = read(channel, body);
        if (relayMessage == null || serverId.value().equals(relayMessage.origin())) {
            return;
        }
        if (relayMessage.v() != RealtimeRelayMessage.VERSION) {
            log.warn("[Relay] dropped: unknown version v={} channel={} origin={}", relayMessage.v(), channel, relayMessage.origin());
            return;
        }
        if (relayMessage.accountIds() != null && relayMessage.accountIds().size() > properties.maxTargets()) {
            log.warn("[Relay] dropped: too many targets channel={} origin={} targets={}",
                    channel, relayMessage.origin(), relayMessage.accountIds().size());
            return;
        }
        if (RealtimeRelayMessage.DELIVER.equals(relayMessage.kind())) {
            deliver(channel, relayMessage);
        } else if (RealtimeRelayMessage.SESSION_CHECK.equals(relayMessage.kind())) {
            checkSessions(relayMessage);
        } else {
            log.warn("[Relay] dropped: unknown kind={} channel={} origin={}", relayMessage.kind(), channel, relayMessage.origin());
        }
    }

    private RealtimeRelayMessage read(String channel, byte[] body) {
        try {
            return reader.readValue(body);
        } catch (RuntimeException e) {
            log.warn("[Relay] dropped: unreadable message channel={} bytes={}", channel, body.length);
            return null;
        }
    }

    private void deliver(String channel, RealtimeRelayMessage relayMessage) {
        if (relayMessage.message() == null) {
            log.warn("[Relay] dropped: DELIVER without message channel={} origin={}", channel, relayMessage.origin());
            return;
        }
        if (channel.equals(properties.globalChannel())) {
            delivery.deliverToAll(relayMessage.message());
            return;
        }
        String channelGuildId = properties.guildIdOf(channel);
        if (channelGuildId != null && !channelGuildId.equals(relayMessage.guildId())) {
            // 잘못 실린 메시지가 다른 길드 사람에게 가지 않게
            log.warn("[Relay] dropped: guild mismatch channel={} guildId={} id={}",
                    channel, relayMessage.guildId(), relayMessage.message().id());
            return;
        }
        delivery.deliverToAccounts(accountIds(relayMessage), relayMessage.message());
    }

    /** 다른 곳 로그인: 그 계정의 연결이 이 서버에 있으면 Pong 때와 같은 판정으로 다시 확인한다(이전 세션이면 4001, 새 세션이면 그대로) */
    private void checkSessions(RealtimeRelayMessage relayMessage) {
        for (Long accountId : accountIds(relayMessage)) {
            registry.find(accountId).ifPresent(heartbeat::revalidate);
        }
    }

    private static List<Long> accountIds(RealtimeRelayMessage relayMessage) {
        if (relayMessage.accountIds() == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(relayMessage.accountIds().size());
        for (String id : relayMessage.accountIds()) {
            try {
                ids.add(Long.valueOf(id));
            } catch (NumberFormatException e) {
                log.warn("[Relay] skipped invalid accountId={} origin={}", id, relayMessage.origin());
            }
        }
        return ids;
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        log.info("[Relay] subscribed channel={} serverId={}", new String(channel, StandardCharsets.UTF_8), serverId);
    }

    @Override
    public void onPatternSubscribed(byte[] pattern, long count) {
        log.info("[Relay] subscribed pattern={} serverId={}", new String(pattern, StandardCharsets.UTF_8), serverId);
    }

}
