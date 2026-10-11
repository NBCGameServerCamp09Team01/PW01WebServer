package com.pw01.webserver.realtime.interceptor;

import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.service.AuthService;
import com.pw01.webserver.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 핸드셰이크 인증 단위 테스트(Docker 불필요). 판정은 AuthService(목)가 하고, 여기서는 결과를 연결 속성·오류 응답으로 바꾸는지만 본다.
 * 실패하면 RealtimeHandshakeInterceptor가 HTTP와 같은 상태 코드·오류 본문(realtime-api.md "핸드셰이크 결과")을 쓰는지 본다.
 */
class RealtimeHandshakeInterceptorTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    private AuthService authService;
    private RealtimeHandshakeInterceptor interceptor;
    private MockHttpServletRequest servletRequest;
    private MockHttpServletResponse servletResponse;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        interceptor = new RealtimeHandshakeInterceptor(authService, JsonMapper.builder().build());
        servletRequest = new MockHttpServletRequest("GET", "/realtime");
        servletResponse = new MockHttpServletResponse();
        attributes = new HashMap<>();
    }

    // 확인: 인증을 통과하면 계정·토큰을 연결 속성에 넣고 연결을 허락한다
    @Test
    void 인증을_통과하면_계정과_토큰을_넣고_허락한다() {
        servletRequest.addHeader("Authorization", "Bearer " + TOKEN);
        when(authService.authenticate("Bearer " + TOKEN)).thenReturn(new AuthenticatedSession(TOKEN, 7L, Instant.now()));

        boolean allowed = handshake();

        assertThat(allowed).isTrue();
        assertThat(attributes).containsEntry(RealtimeHandshakeInterceptor.ACCOUNT_ID_ATTRIBUTE, 7L)
                .containsEntry(RealtimeHandshakeInterceptor.TOKEN_ATTRIBUTE, TOKEN);
    }

    // 확인: 인증 실패는 HTTP와 같은 401과 오류 코드로 거절한다
    @Test
    void 인증에_실패하면_401과_오류_코드로_거절한다() throws Exception {
        when(authService.authenticate(null)).thenThrow(new UnauthorizedException("AUTH_TOKEN_MISSING", "로그인이 필요합니다."));

        boolean allowed = handshake();

        assertThat(allowed).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(401);
        assertThat(servletResponse.getContentAsString()).contains("\"code\":\"AUTH_TOKEN_MISSING\"").contains("\"retryable\":false");
    }

    // 확인: Redis에 닿지 못하면 503 SERVICE_UNAVAILABLE(retryable true)
    @Test
    void 저장소_장애면_503으로_거절한다() throws Exception {
        servletRequest.addHeader("Authorization", "Bearer " + TOKEN);
        when(authService.authenticate("Bearer " + TOKEN)).thenThrow(new DataAccessResourceFailureException("redis down"));

        boolean allowed = handshake();

        assertThat(allowed).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(503);
        assertThat(servletResponse.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").contains("\"retryable\":true");
    }

    private boolean handshake() {
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest), response,
                mock(WebSocketHandler.class), attributes);
        response.close();
        return allowed;
    }

}
