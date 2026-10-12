package com.pw01.webserver.realtime.handler;

import com.pw01.webserver.realtime.interceptor.RealtimeHandshakeInterceptor;
import com.pw01.webserver.realtime.service.RealtimeCloseStatus;
import com.pw01.webserver.realtime.service.RealtimeConnection;
import com.pw01.webserver.realtime.service.RealtimeConnectionRegistry;
import com.pw01.webserver.realtime.service.RealtimeHeartbeat;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /realtime 연결의 생애(루트 docs/contracts/realtime-api.md). 연결되면 계정당 하나로 등록하고, Pong은 heartbeat로 넘긴다.
 * v1에는 게임 → 서버 메시지가 없으므로 게임이 보낸 텍스트는 읽지 않고 버린다.
 */
@Component
public class RealtimeWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);
    private static final String CONNECTION_ATTRIBUTE = RealtimeWebSocketHandler.class.getName() + ".connection";

    private final RealtimeConnectionRegistry registry;
    private final RealtimeHeartbeat heartbeat;
    private final RealtimeNotifier notifier;

    public RealtimeWebSocketHandler(RealtimeConnectionRegistry registry, RealtimeHeartbeat heartbeat,
                                    RealtimeNotifier notifier) {
        this.registry = registry;
        this.heartbeat = heartbeat;
        this.notifier = notifier;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long accountId = (Long) session.getAttributes().get(RealtimeHandshakeInterceptor.ACCOUNT_ID_ATTRIBUTE);
        String token = (String) session.getAttributes().get(RealtimeHandshakeInterceptor.TOKEN_ATTRIBUTE);
        RealtimeConnection connection = new RealtimeConnection(session, accountId, token);
        session.getAttributes().put(CONNECTION_ATTRIBUTE, connection);
        log.info("[Realtime] connected {}", connection);

        // 계정당 연결 하나: 같은 세션이면 게임의 재연결(4000), 다른 세션이면 이전 세션이 밀려난 것(알리고 4001)
        registry.register(connection).ifPresent(previous -> {
            if (previous.hasSameToken(token)) {
                previous.close(RealtimeCloseStatus.CONNECTION_REPLACED);
            } else {
                notifier.notifySessionReplaced(previous);
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("[Realtime] text from client ignored. session={}", session.getId());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        RealtimeConnection connection = connectionOf(session);
        if (connection != null) {
            heartbeat.onPong(connection);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("[Realtime] transport error. session={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RealtimeConnection connection = connectionOf(session);
        if (connection != null) {
            registry.unregister(connection);
            log.info("[Realtime] closed {} status={}", connection, status);
        }
    }

    private static RealtimeConnection connectionOf(WebSocketSession session) {
        return (RealtimeConnection) session.getAttributes().get(CONNECTION_ATTRIBUTE);
    }

}
