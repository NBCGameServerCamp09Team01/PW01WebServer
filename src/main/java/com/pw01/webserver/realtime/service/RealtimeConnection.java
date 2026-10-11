package com.pw01.webserver.realtime.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 로그인한 게임 하나의 실시간 연결. 핸드셰이크에서 확인한 계정과 토큰을 함께 들고 다닌다.
 * Ping(스케줄러)·알림(로그인 이벤트)·Pong 처리가 다른 스레드에서 동시에 보낼 수 있으므로 세션을 동시 전송용 데코레이터로 감싼다.
 * 토큰 원문은 Pong 때 세션 연장에만 쓰고 로그에 남기지 않는다.
 */
public final class RealtimeConnection {

    private static final Logger log = LoggerFactory.getLogger(RealtimeConnection.class);

    /** 보내기가 이 시간보다 오래 막히면 연결을 버린다(느린 게임 하나가 서버 스레드를 붙잡지 않게) */
    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
    /** 보내지 못하고 쌓인 메시지가 이보다 크면 연결을 버린다 */
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final WebSocketSession session;
    private final Long accountId;
    private final String token;
    /** 마지막 Pong 뒤로 보낸 Ping 수. Pong을 받으면 0 */
    private final AtomicInteger missedPongs = new AtomicInteger();

    public RealtimeConnection(WebSocketSession session, Long accountId, String token) {
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
        this.accountId = accountId;
        this.token = token;
    }

    public Long accountId() {
        return accountId;
    }

    /** Pong 때 세션 연장에 넘긴다. 로그에 남기지 않는다 */
    String token() {
        return token;
    }

    /** 같은 세션(토큰)의 연결인지. 게임의 재연결과 다른 곳 로그인을 가른다 */
    public boolean hasSameToken(String otherToken) {
        return token.equals(otherToken);
    }

    public String sessionId() {
        return session.getId();
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    public int missedPongs() {
        return missedPongs.get();
    }

    void markPingSent() {
        missedPongs.incrementAndGet();
    }

    void markPongReceived() {
        missedPongs.set(0);
    }

    void send(WebSocketMessage<?> message) throws IOException {
        session.sendMessage(message);
    }

    /** 이미 닫혔거나 닫다가 실패해도 예외를 밖으로 던지지 않는다(닫기는 늘 마지막 처리라 되돌릴 것이 없음) */
    public void close(CloseStatus status) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException | RuntimeException e) {
            log.debug("[Realtime] close failed. accountId={} session={} status={}", accountId, sessionId(), status, e);
        }
    }

    @Override
    public String toString() {
        return "RealtimeConnection[accountId=" + accountId + ", session=" + sessionId() + "]";
    }

}
