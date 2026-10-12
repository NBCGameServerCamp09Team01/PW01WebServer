package com.pw01.webserver.realtime.handler;

import com.pw01.webserver.realtime.interceptor.RealtimeHandshakeInterceptor;
import com.pw01.webserver.realtime.service.RealtimeCloseStatus;
import com.pw01.webserver.realtime.service.RealtimeConnection;
import com.pw01.webserver.realtime.service.RealtimeConnectionRegistry;
import com.pw01.webserver.realtime.service.RealtimeHeartbeat;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 연결 등록 단위 테스트(Docker 불필요). 계정당 연결 하나, 새 것이 이긴다(realtime-api.md "같은 계정의 연결이 겹칠 때").
 * 실패하면 afterConnectionEstablished의 이전 연결 처리(같은 토큰 4000 / 다른 토큰 알림 + 4001)나 등록부 해제 조건을 본다.
 */
class RealtimeWebSocketHandlerTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";
    private static final String OTHER_TOKEN = "ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqpon-_98";
    private static final Long ACCOUNT_ID = 1L;

    private RealtimeConnectionRegistry registry;
    private RealtimeNotifier notifier;
    private RealtimeWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        registry = new RealtimeConnectionRegistry();
        notifier = mock(RealtimeNotifier.class);
        handler = new RealtimeWebSocketHandler(registry, mock(RealtimeHeartbeat.class), notifier);
    }

    // 확인: 같은 토큰으로 다시 연결하면(게임의 재연결) 이전 연결을 4000으로 닫고 새 연결을 남긴다
    @Test
    void 같은_세션의_새_연결이면_이전_연결을_4000으로_닫는다() throws Exception {
        WebSocketSession first = socket("s1", TOKEN);
        WebSocketSession second = socket("s2", TOKEN);
        handler.afterConnectionEstablished(first);

        handler.afterConnectionEstablished(second);

        verify(first).close(RealtimeCloseStatus.CONNECTION_REPLACED);
        verify(notifier, never()).notifySessionReplaced(any());
        assertThat(registry.find(ACCOUNT_ID)).get().extracting(RealtimeConnection::sessionId).isEqualTo("s2");
    }

    // 확인: 다른 토큰으로 연결이 오면 이전 세션이 밀려난 것이므로 이전 연결에 알린다(알림 + 4001은 notifier 몫)
    @Test
    void 다른_세션의_연결이면_이전_연결에_다른_곳_로그인을_알린다() throws Exception {
        handler.afterConnectionEstablished(socket("s1", TOKEN));

        handler.afterConnectionEstablished(socket("s2", OTHER_TOKEN));

        verify(notifier).notifySessionReplaced(any());
    }

    // 확인: 밀려난 이전 연결이 닫혀도 새 연결은 등록부에 남는다
    @Test
    void 이전_연결이_닫혀도_새_연결은_남는다() throws Exception {
        WebSocketSession first = socket("s1", TOKEN);
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(socket("s2", TOKEN));

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);

        assertThat(registry.find(ACCOUNT_ID)).get().extracting(RealtimeConnection::sessionId).isEqualTo("s2");
    }

    private static WebSocketSession socket(String id, String token) {
        WebSocketSession socket = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(RealtimeHandshakeInterceptor.ACCOUNT_ID_ATTRIBUTE, ACCOUNT_ID);
        attributes.put(RealtimeHandshakeInterceptor.TOKEN_ATTRIBUTE, token);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.getId()).thenReturn(id);
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }

}
