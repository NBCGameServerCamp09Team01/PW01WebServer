package com.pw01.webserver.presence;

import com.pw01.webserver.presence.event.PresenceChangedEvent;
import com.pw01.webserver.presence.repository.RedisPresenceStore;
import com.pw01.webserver.presence.service.PresenceService;
import com.pw01.webserver.common.server.ServerId;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 접속 상태 서비스를 Redis 없이 가짜 저장소로 본다.
 * (1) Redis 장애 때 밖으로 예외를 던지지 않는지(연결·Pong·길드 조회를 막지 않음)
 * (2) 상태가 실제로 바뀔 때만 PresenceChangedEvent를 내는지(키가 새로 생김 / 이 서버가 지움)
 * 실패하면 PresenceService의 try·catch, markOnline·release의 반환값을 보는 if를 먼저 의심한다.
 */
class PresenceServiceTest {

    private final RedisPresenceStore store = mock(RedisPresenceStore.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PresenceService presence = new PresenceService(store, ServerId.of("srv-a"), events);

    // 확인: 쓰기·지우기 실패는 삼킨다(경고 로그만), 이벤트도 내지 않는다
    @Test
    void 쓰기_실패는_던지지_않음() {
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        doThrow(down).when(store).markOnline(anyLong(), anyString());
        when(store.release(anyLong(), anyString())).thenThrow(down);

        assertThatCode(() -> presence.connected(1L, "srv-a")).doesNotThrowAnyException();
        assertThatCode(() -> presence.alive(1L)).doesNotThrowAnyException();
        assertThatCode(() -> presence.disconnected(1L, "srv-a")).doesNotThrowAnyException();
        verify(events, never()).publishEvent(any(Object.class));
    }

    // 확인: 읽기 실패면 모두 접속 안 함(빈 집합)
    @Test
    void 읽기_실패면_빈_집합() {
        when(store.onlineOf(any())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(presence.onlineOf(List.of(1L, 2L))).isEmpty();
    }

    // 확인: 키가 없던 연결은 접속함, 이미 있던 연결(다시 붙음·다른 서버로 옮김)은 알리지 않음
    @Test
    void 처음_연결만_접속함을_알림() {
        when(store.markOnline(1L, "srv-a")).thenReturn(true);
        when(store.markOnline(2L, "srv-b")).thenReturn(false);

        presence.connected(1L, "srv-a");
        presence.connected(2L, "srv-b");

        verify(events).publishEvent(new PresenceChangedEvent(1L, true));
        verify(events, never()).publishEvent(new PresenceChangedEvent(2L, true));
    }

    // 확인: Pong은 보통 알리지 않고, 키가 사라졌다가 다시 생긴 경우만 접속함
    @Test
    void Pong은_키가_다시_생길_때만_알림() {
        when(store.markOnline(1L, "srv-a")).thenReturn(false);
        when(store.markOnline(2L, "srv-a")).thenReturn(true);

        presence.alive(1L);
        presence.alive(2L);

        verify(events, never()).publishEvent(new PresenceChangedEvent(1L, true));
        verify(events).publishEvent(new PresenceChangedEvent(2L, true));
    }

    // 확인: 이 서버가 실제로 지웠을 때만 끊김(다른 서버로 이미 다시 붙어 못 지웠으면 알리지 않음)
    @Test
    void 지운_끊김만_알림() {
        when(store.release(1L, "srv-a")).thenReturn(true);
        when(store.release(2L, "srv-a")).thenReturn(false);

        presence.disconnected(1L, "srv-a");
        presence.disconnected(2L, "srv-a");

        verify(events).publishEvent(new PresenceChangedEvent(1L, false));
        verify(events, never()).publishEvent(new PresenceChangedEvent(2L, false));
    }

    // 확인: 받는 쪽(길드 알림)이 예외를 던져도 연결 처리는 막지 않는다
    @Test
    void 받는_쪽_실패는_삼킴() {
        when(store.markOnline(1L, "srv-a")).thenReturn(true);
        doThrow(new IllegalStateException("listener")).when(events).publishEvent(any(Object.class));

        assertThatCode(() -> presence.connected(1L, "srv-a")).doesNotThrowAnyException();
    }

}
