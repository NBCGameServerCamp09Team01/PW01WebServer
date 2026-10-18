package com.pw01.webserver.realtime.repository;

import com.pw01.webserver.config.RealtimeRelayProperties;
import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeRelayMessage;
import com.pw01.webserver.common.server.ServerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;

/**
 * 서버 사이 메시지를 Redis 채널에 올린다(루트 docs/contracts/redis-keys.md "실시간 서버 사이 전달").
 * 올리기에 실패해도 예외를 밖으로 던지지 않는다: 이 서버 사람에게는 이미 보냈고, 다른 서버 사람은 놓친다(명세 "놓칠 수 있음").
 * pw01.realtime.relay.enabled=false면 아무것도 하지 않는다.
 */
@Repository
public class RealtimeRelayPublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRelayPublisher.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final RealtimeRelayProperties properties;
    private final ServerId serverId;

    public RealtimeRelayPublisher(StringRedisTemplate redis, JsonMapper jsonMapper, RealtimeRelayProperties properties,
                                  ServerId serverId) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.serverId = serverId;
    }

    public void publishToAll(RealtimeMessage message) {
        publish(properties.globalChannel(), RealtimeRelayMessage.deliver(serverId.value(), null, null, message));
    }

    public void publishToGuild(String guildId, Collection<Long> memberIds, RealtimeMessage message) {
        publish(properties.guildChannel(guildId),
                RealtimeRelayMessage.deliver(serverId.value(), guildId, toStrings(memberIds), message));
    }

    public void publishToAccounts(Collection<Long> accountIds, RealtimeMessage message) {
        publish(properties.targetChannel(),
                RealtimeRelayMessage.deliver(serverId.value(), null, toStrings(accountIds), message));
    }

    public void publishSessionCheck(Long accountId) {
        publish(properties.targetChannel(), RealtimeRelayMessage.sessionCheck(serverId.value(), List.of(accountId.toString())));
    }

    /** 모든 서버에 "웨이브 정의가 바뀌었다"(전체 채널, 게임에는 가지 않음) */
    public void publishContentChanged() {
        publish(properties.globalChannel(), RealtimeRelayMessage.contentChanged(serverId.value()));
    }

    private void publish(String channel, RealtimeRelayMessage relayMessage) {
        if (!properties.enabled()) {
            return;
        }
        try {
            redis.convertAndSend(channel, jsonMapper.writeValueAsString(relayMessage));
            log.debug("[Relay] published channel={} kind={} id={}", channel, relayMessage.kind(), messageIdOf(relayMessage));
        } catch (RuntimeException e) {
            log.warn("[Relay] publish failed channel={} kind={} id={}", channel, relayMessage.kind(), messageIdOf(relayMessage), e);
        }
    }

    private static String messageIdOf(RealtimeRelayMessage relayMessage) {
        return relayMessage.message() == null ? "-" : relayMessage.message().id();
    }

    private static List<String> toStrings(Collection<Long> accountIds) {
        return accountIds.stream().map(String::valueOf).toList();
    }

}
