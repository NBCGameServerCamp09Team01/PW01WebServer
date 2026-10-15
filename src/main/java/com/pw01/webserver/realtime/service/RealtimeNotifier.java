package com.pw01.webserver.realtime.service;

import com.pw01.webserver.config.RealtimeRelayProperties;
import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.repository.RealtimeRelayPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 게임에 알림을 보내는 입구. 다른 기능(채팅·길드·우편함·공지)은 이 클래스의 보내기 넷만 부른다(뼈대).
 * 받는 사람이 어느 서버에 붙어 있어도 간다: 이 서버 연결에는 바로 보내고, 같은 메시지를 Redis 채널에 올려 다른 서버가 자기 연결에 보낸다
 * (루트 docs/contracts/realtime-api.md "서버 여러 대", redis-keys.md "실시간 서버 사이 전달").
 * <p>
 * 부르는 쪽 규칙: DB 커밋 뒤에 부른다. 놓칠 수 있다(반환값 없음, 보내기 실패로 예외를 던지지 않음 — 상태는 게임이 HTTP로 다시 받는다).
 * 한도(계정 수·메시지 크기)를 넘으면 보내지 않고 경고 로그만 남긴다.
 */
@Service
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);
    /** 길드 ID는 채널 이름에 그대로 들어간다. 패턴 문자(*?[])·공백·':'가 섞이면 다른 길드 채널을 건드릴 수 있어 막는다 */
    private static final Pattern GUILD_ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");

    private final RealtimeLocalDelivery delivery;
    private final RealtimeRelayPublisher publisher;
    private final RealtimeRelayProperties properties;
    private final JsonMapper jsonMapper;

    public RealtimeNotifier(RealtimeLocalDelivery delivery, RealtimeRelayPublisher publisher,
                            RealtimeRelayProperties properties, JsonMapper jsonMapper) {
        this.delivery = delivery;
        this.publisher = publisher;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /** 한 사람에게(지정 채널) */
    public void send(Long accountId, RealtimeMessage message) {
        Objects.requireNonNull(accountId, "accountId");
        if (!withinLimits(message, 1)) {
            return;
        }
        delivery.deliverToAccount(accountId, message);
        publisher.publishToAccounts(List.of(accountId), message);
    }

    /** 여러 사람에게(길드와 상관없는 묶음, 지정 채널). 목록은 부르는 쪽이 만든다. 길드에 관한 메시지는 sendToGuild */
    public void sendToAccounts(Collection<Long> accountIds, RealtimeMessage message) {
        List<Long> targets = distinct(accountIds);
        if (targets.isEmpty() || !withinLimits(message, targets.size())) {
            return;
        }
        delivery.deliverToAccounts(targets, message);
        publisher.publishToAccounts(targets, message);
    }

    /**
     * 한 길드에게: 그 길드 채널(pw01:realtime-relay:guild:&lt;guildId&gt;)로. 길드원 목록은 부르는 쪽이 넣는다(GuildDirectory.memberIds).
     * 탈퇴 알림처럼 막 나간 사람에게도 보내야 하면 그 사람을 목록에 더해 넣는다.
     *
     * @throws IllegalArgumentException guildId가 영문·숫자·_·- 1~64자가 아니면(부르는 쪽 실수)
     */
    public void sendToGuild(String guildId, Collection<Long> memberIds, RealtimeMessage message) {
        if (guildId == null || !GUILD_ID.matcher(guildId).matches()) {
            throw new IllegalArgumentException("guildId는 영문·숫자·_·- 1~64자여야 합니다: " + guildId);
        }
        List<Long> targets = distinct(memberIds);
        if (targets.isEmpty() || !withinLimits(message, targets.size())) {
            return;
        }
        delivery.deliverToAccounts(targets, message);
        publisher.publishToGuild(guildId, targets, message);
    }

    /** 지금 접속한 모든 사람에게(전체 채널) */
    public void sendToAll(RealtimeMessage message) {
        if (!withinLimits(message, 0)) {
            return;
        }
        delivery.deliverToAll(message);
        publisher.publishToAll(message);
    }

    /** "다른 곳 로그인"을 알리고 4001로 닫는다(이 서버의 연결). 보내기에 실패해도 닫는다(게임은 종료 코드만으로도 같은 처리를 한다) */
    public void notifySessionReplaced(RealtimeConnection connection) {
        delivery.sendAndClose(connection, RealtimeMessage.of(RealtimeMessageType.SESSION_REPLACED),
                RealtimeCloseStatus.SESSION_REPLACED);
    }

    /** 다른 서버들에 "이 계정 연결의 세션을 다시 확인하라"를 넘긴다(다른 곳 로그인, realtime-api.md "같은 계정의 연결이 겹칠 때") */
    public void requestSessionCheck(Long accountId) {
        publisher.publishSessionCheck(Objects.requireNonNull(accountId, "accountId"));
    }

    private boolean withinLimits(RealtimeMessage message, int targetCount) {
        Objects.requireNonNull(message, "message");
        if (targetCount > properties.maxTargets()) {
            log.warn("[Realtime] not sent: too many targets type={} id={} targets={} max={}",
                    message.type(), message.id(), targetCount, properties.maxTargets());
            return false;
        }
        int size = jsonMapper.writeValueAsBytes(message).length;
        if (size > properties.maxMessageBytes()) {
            log.warn("[Realtime] not sent: message too large type={} id={} bytes={} max={}",
                    message.type(), message.id(), size, properties.maxMessageBytes());
            return false;
        }
        return true;
    }

    private static List<Long> distinct(Collection<Long> accountIds) {
        Objects.requireNonNull(accountIds, "accountIds");
        return accountIds.stream().filter(Objects::nonNull).distinct().toList();
    }

}
