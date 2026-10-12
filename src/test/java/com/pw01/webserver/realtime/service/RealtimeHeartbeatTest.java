package com.pw01.webserver.realtime.service;

import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.service.AuthService;
import com.pw01.webserver.common.error.UnauthorizedException;
import com.pw01.webserver.config.AuthProperties;
import com.pw01.webserver.config.RealtimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * heartbeat 단위 테스트(Docker 불필요). 소켓은 목, 세션 판정(AuthService)도 목이다.
 * 실패하면 RealtimeHeartbeat의 Ping 횟수 세기·Pong 연장 결과별 종료 코드(realtime-api.md "Heartbeat")가 바뀌었는지 본다.
 */
class RealtimeHeartbeatTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";
    private static final Long ACCOUNT_ID = 1L;

    private WebSocketSession socket;
    private AuthService authService;
    private RealtimeConnectionRegistry registry;
    private RealtimeHeartbeat heartbeat;
    private RealtimeConnection connection;

    @BeforeEach
    void setUp() {
        socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("s1");
        authService = mock(AuthService.class);
        registry = new RealtimeConnectionRegistry();
        RealtimeNotifier notifier = new RealtimeNotifier(registry, JsonMapper.builder().build());
        heartbeat = new RealtimeHeartbeat(registry, notifier, authService,
                new RealtimeProperties(Duration.ofSeconds(60), 3), authProperties(Duration.ofMinutes(10)));
        connection = new RealtimeConnection(socket, ACCOUNT_ID, TOKEN);
        registry.register(connection);
    }

    // 확인: Pong 없이 Ping을 허용 횟수(3)만큼 보낸 다음 차례에 4003으로 닫는다. 실패하면 놓친 Pong 세기를 본다
    @Test
    void Pong이_연속으로_없으면_4003으로_닫는다() throws Exception {
        heartbeat.sendPings();
        heartbeat.sendPings();
        heartbeat.sendPings();
        verify(socket, times(3)).sendMessage(any(PingMessage.class));
        verify(socket, never()).close(any());

        heartbeat.sendPings();

        verify(socket).close(RealtimeCloseStatus.HEARTBEAT_TIMEOUT);
    }

    // 확인: Pong을 받으면 놓친 수가 0이 되고 세션을 다시 건다. 실패하면 onPong이 연장을 부르는지 본다
    @Test
    void Pong을_받으면_세션을_다시_걸고_놓친_수를_지운다() {
        heartbeat.sendPings();
        heartbeat.sendPings();
        when(authService.extendSession(TOKEN)).thenReturn(new AuthenticatedSession(TOKEN, ACCOUNT_ID, Instant.now()));

        heartbeat.onPong(connection);

        verify(authService).extendSession(TOKEN);
        assertThat(connection.missedPongs()).isZero();
    }

    // 확인: 연장 결과가 "다른 곳 로그인"이면 SESSION_REPLACED 메시지를 보내고 4001로 닫는다
    @Test
    void 다른_곳_로그인이면_알리고_4001로_닫는다() throws Exception {
        when(authService.extendSession(TOKEN)).thenThrow(new UnauthorizedException("AUTH_SESSION_REPLACED", "다른 곳"));

        heartbeat.onPong(connection);

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket).sendMessage(sent.capture());
        assertThat(sent.getValue().getPayload()).contains("\"type\":\"SESSION_REPLACED\"").contains("\"data\":{}");
        verify(socket).close(RealtimeCloseStatus.SESSION_REPLACED);
    }

    // 확인: 세션이 없으면(만료·로그아웃) 4002로 닫는다
    @Test
    void 세션이_없으면_4002로_닫는다() throws Exception {
        when(authService.extendSession(TOKEN)).thenThrow(new UnauthorizedException("AUTH_SESSION_NOT_FOUND", "끝남"));

        heartbeat.onPong(connection);

        verify(socket).close(RealtimeCloseStatus.SESSION_ENDED);
    }

    // 확인: Redis에 닿지 못하면 세션 상태를 모르므로 닫지 않는다(다음 Pong에서 다시 본다)
    @Test
    void 저장소_장애면_연결을_닫지_않는다() throws Exception {
        when(authService.extendSession(TOKEN)).thenThrow(new DataAccessResourceFailureException("redis down"));

        heartbeat.onPong(connection);

        verify(socket, never()).close(any(CloseStatus.class));
    }

    // 확인: Ping 간격 × 허용 횟수가 세션 수명 이상이면 서버가 뜨지 않는다(realtime-api.md 값의 관계)
    @Test
    void 값의_관계가_틀리면_뜨지_않는다() {
        RealtimeHeartbeat wrong = new RealtimeHeartbeat(registry, mock(RealtimeNotifier.class), authService,
                new RealtimeProperties(Duration.ofMinutes(5), 2), authProperties(Duration.ofMinutes(10)));

        assertThatThrownBy(wrong::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    private static AuthProperties authProperties(Duration ttl) {
        return new AuthProperties(new AuthProperties.Session(ttl),
                new AuthProperties.LoginFail(5, Duration.ofMinutes(1), Duration.ofMinutes(1)));
    }

}
