package com.pw01.webserver.realtime.service;

import com.pw01.webserver.auth.event.SessionEndedEvent;
import com.pw01.webserver.auth.event.SessionIssuedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로그인·로그아웃 이벤트 → 실시간 연결 반영 단위 테스트(Docker 불필요).
 * 실패하면 RealtimeSessionListener가 예외를 밖으로 던져 로그인·로그아웃을 실패시키는지 먼저 본다.
 */
class RealtimeSessionListenerTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";
    private static final String OTHER_TOKEN = "ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqpon-_98";
    private static final Long ACCOUNT_ID = 1L;

    private WebSocketSession socket;
    private RealtimeConnectionRegistry registry;
    private RealtimeNotifier notifier;
    private RealtimeSessionListener listener;

    @BeforeEach
    void setUp() {
        socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("s1");
        registry = new RealtimeConnectionRegistry();
        notifier = mock(RealtimeNotifier.class);
        listener = new RealtimeSessionListener(registry, notifier);
    }

    // 확인: 새 로그인이면 그 계정의 지금 연결(이전 세션)에 "다른 곳 로그인"을 알린다
    @Test
    void 새_로그인이면_이전_연결에_알린다() {
        RealtimeConnection previous = new RealtimeConnection(socket, ACCOUNT_ID, TOKEN);
        registry.register(previous);

        listener.onSessionIssued(new SessionIssuedEvent(ACCOUNT_ID));

        verify(notifier).notifySessionReplaced(previous);
    }

    // 확인: 알림이 실패해도 예외가 밖으로 나가지 않는다(로그인이 실패하면 안 됨)
    @Test
    void 알림이_실패해도_로그인을_막지_않는다() {
        registry.register(new RealtimeConnection(socket, ACCOUNT_ID, TOKEN));
        doThrow(new IllegalStateException("send failed")).when(notifier).notifySessionReplaced(any());

        assertThatCode(() -> listener.onSessionIssued(new SessionIssuedEvent(ACCOUNT_ID))).doesNotThrowAnyException();
    }

    // 확인: 로그아웃은 그 세션의 연결만 1000으로 닫는다
    @Test
    void 로그아웃하면_그_세션의_연결을_1000으로_닫는다() throws Exception {
        registry.register(new RealtimeConnection(socket, ACCOUNT_ID, TOKEN));

        listener.onSessionEnded(new SessionEndedEvent(ACCOUNT_ID, TOKEN));

        verify(socket).close(RealtimeCloseStatus.LOGGED_OUT);
    }

    // 확인: 끝난 세션과 다른 토큰의 연결(다른 곳에서 로그인한 새 세션)은 닫지 않는다
    @Test
    void 로그아웃한_세션이_아닌_연결은_닫지_않는다() throws Exception {
        registry.register(new RealtimeConnection(socket, ACCOUNT_ID, OTHER_TOKEN));

        listener.onSessionEnded(new SessionEndedEvent(ACCOUNT_ID, TOKEN));

        verify(socket, never()).close(any(CloseStatus.class));
    }

}
