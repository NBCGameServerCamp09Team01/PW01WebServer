package com.pw01.webserver.presence;

import com.pw01.webserver.presence.repository.RedisPresenceStore;
import com.pw01.webserver.presence.service.PresenceService;
import com.pw01.webserver.realtime.service.RealtimeServerId;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 장애 때 접속 상태가 밖으로 예외를 던지지 않는지(연결·Pong·길드 조회를 막지 않음) 본다. Redis 없이 가짜 저장소로.
 * 실패하면 PresenceService의 try·catch를 먼저 의심한다.
 */
class PresenceServiceTest {

    private final RedisPresenceStore store = mock(RedisPresenceStore.class);
    private final PresenceService presence = new PresenceService(store, RealtimeServerId.of("srv-a"));

    // 확인: 쓰기·지우기 실패는 삼킨다(경고 로그만)
    @Test
    void 쓰기_실패는_던지지_않음() {
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        doThrow(down).when(store).markOnline(anyLong(), anyString());
        when(store.release(anyLong(), anyString())).thenThrow(down);

        assertThatCode(() -> presence.connected(1L, "srv-a")).doesNotThrowAnyException();
        assertThatCode(() -> presence.alive(1L)).doesNotThrowAnyException();
        assertThatCode(() -> presence.disconnected(1L, "srv-a")).doesNotThrowAnyException();
    }

    // 확인: 읽기 실패면 모두 접속 안 함(빈 집합)
    @Test
    void 읽기_실패면_빈_집합() {
        when(store.onlineOf(any())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(presence.onlineOf(List.of(1L, 2L))).isEmpty();
    }

}
