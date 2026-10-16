package com.pw01.webserver.presence;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.presence.event.PresenceChangedEvent;
import com.pw01.webserver.presence.service.PresenceService;
import com.pw01.webserver.realtime.event.RealtimeAliveEvent;
import com.pw01.webserver.realtime.event.RealtimeConnectedEvent;
import com.pw01.webserver.realtime.event.RealtimeDisconnectedEvent;
import com.pw01.webserver.realtime.service.RealtimeServerId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 연결 이벤트 → 접속 상태 키(pw01:presence:&lt;계정&gt;)와 읽기(MGET). 루트 docs/contracts/redis-keys.md "접속 상태" v1.6.
 * 이벤트는 realtime이 내는 것과 같은 모양으로 직접 낸다(WebSocket 없이 이벤트 → 키만 본다).
 * 상태가 바뀔 때만 PresenceChangedEvent가 나는지도 진짜 Redis(SET … GET, Lua 지우기 결과)로 본다(M23 Lv2).
 * 실패하면 PresenceEventListener 연결, presence_release.lua의 비교, 설정 pw01.presence.ttl, markOnline의 setGet을 먼저 의심한다.
 * 테스트끼리 Redis를 함께 쓰므로 계정 ID를 서로 다르게 쓴다(DB 계정이 필요 없어 큰 번호).
 */
@IntegrationTest
@RecordApplicationEvents
class PresenceTest {

    @Autowired
    ApplicationEvents recorded;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PresenceService presence;

    @Autowired
    RealtimeServerId serverId;

    @Autowired
    StringRedisTemplate redis;

    // 확인: 연결되면 그 서버 번호가 들어가고 수명은 설정 값(150초) 안쪽
    @Test
    void 연결되면_서버_번호와_수명() {
        events.publishEvent(new RealtimeConnectedEvent(9_100_001L, "srv-a"));

        assertThat(redis.opsForValue().get("pw01:presence:9100001")).isEqualTo("srv-a");
        assertThat(redis.getExpire("pw01:presence:9100001", TimeUnit.SECONDS)).isBetween(140L, 150L);
    }

    // 확인: 끊김은 값이 그 서버 번호일 때만 지운다(다른 서버로 다시 붙은 값은 남김)
    @Test
    void 끊김은_내_서버_값일_때만() {
        events.publishEvent(new RealtimeConnectedEvent(9_100_002L, "srv-b"));

        events.publishEvent(new RealtimeDisconnectedEvent(9_100_002L, "srv-a", 1006));
        assertThat(redis.opsForValue().get("pw01:presence:9100002")).isEqualTo("srv-b");

        events.publishEvent(new RealtimeDisconnectedEvent(9_100_002L, "srv-b", 1006));
        assertThat(redis.hasKey("pw01:presence:9100002")).isFalse();
    }

    // 확인: 살아 있음(Pong)은 이 서버 번호로 다시 쓰고 수명을 다시 채운다
    @Test
    void 살아_있음은_수명을_다시_채움() {
        redis.opsForValue().set("pw01:presence:9100003", serverId.value(), Duration.ofSeconds(5));

        events.publishEvent(new RealtimeAliveEvent(9_100_003L));

        assertThat(redis.opsForValue().get("pw01:presence:9100003")).isEqualTo(serverId.value());
        assertThat(redis.getExpire("pw01:presence:9100003", TimeUnit.SECONDS)).isGreaterThan(100L);
    }

    // 확인: 여러 계정을 한 번에 읽어 접속한 사람만 돌려준다, 빈 목록은 빈 집합
    @Test
    void 여러_계정_한_번에_읽기() {
        events.publishEvent(new RealtimeConnectedEvent(9_100_004L, "srv-a"));
        events.publishEvent(new RealtimeConnectedEvent(9_100_006L, "srv-b"));

        assertThat(presence.onlineOf(List.of(9_100_004L, 9_100_005L, 9_100_006L)))
                .containsExactlyInAnyOrder(9_100_004L, 9_100_006L);
        assertThat(presence.onlineOf(List.of())).isEmpty();
    }

    // 확인: 같은 계정이 두 번 붙고 Pong이 와도 접속함 알림은 처음 한 번뿐
    @Test
    void 접속함은_처음_한_번만() {
        events.publishEvent(new RealtimeConnectedEvent(9_100_007L, "srv-a"));
        events.publishEvent(new RealtimeConnectedEvent(9_100_007L, "srv-b"));
        events.publishEvent(new RealtimeAliveEvent(9_100_007L));

        assertThat(changes(9_100_007L)).containsExactly(new PresenceChangedEvent(9_100_007L, true));
    }

    // 확인: 끊김 알림은 그 서버가 실제로 지웠을 때만(다른 서버 값이면 지우지도 알리지도 않음)
    @Test
    void 끊김은_지웠을_때만() {
        events.publishEvent(new RealtimeConnectedEvent(9_100_008L, "srv-b"));

        events.publishEvent(new RealtimeDisconnectedEvent(9_100_008L, "srv-a", 1006));
        events.publishEvent(new RealtimeDisconnectedEvent(9_100_008L, "srv-b", 1006));
        events.publishEvent(new RealtimeDisconnectedEvent(9_100_008L, "srv-b", 1006));

        assertThat(changes(9_100_008L)).containsExactly(
                new PresenceChangedEvent(9_100_008L, true),
                new PresenceChangedEvent(9_100_008L, false));
    }

    private List<PresenceChangedEvent> changes(Long accountId) {
        return recorded.stream(PresenceChangedEvent.class)
                .filter(event -> event.accountId().equals(accountId))
                .toList();
    }

}
