package com.pw01.webserver.realtime.interceptor;

import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.service.AuthService;
import com.pw01.webserver.common.error.ApiException;
import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

/**
 * 실시간 연결의 핸드셰이크 인증(루트 docs/contracts/realtime-api.md "연결"·"핸드셰이크 결과").
 * HTTP와 같은 AuthService.authenticate로 판정하고(같은 401 코드 네 가지, 통과하면 세션 연장), 실패하면 공통 오류 본문으로 답한다.
 * /realtime은 MVC 인증 인터셉터 대상이 아니므로(PublicPaths) 여기서 따로 인증한다.
 * 토큰은 쿼리 문자열이 아니라 Authorization 헤더로만 받는다.
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    /** 핸드셰이크를 통과한 계정 ID. RealtimeWebSocketHandler가 꺼낸다 */
    public static final String ACCOUNT_ID_ATTRIBUTE = RealtimeHandshakeInterceptor.class.getName() + ".accountId";
    /** 핸드셰이크를 통과한 토큰 원문(Pong 때 세션 연장에 씀). 로그에 남기지 않는다 */
    public static final String TOKEN_ATTRIBUTE = RealtimeHandshakeInterceptor.class.getName() + ".token";

    private static final Logger log = LoggerFactory.getLogger(RealtimeHandshakeInterceptor.class);

    private final AuthService authService;
    private final JsonMapper jsonMapper;

    public RealtimeHandshakeInterceptor(AuthService authService, JsonMapper jsonMapper) {
        this.authService = authService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        try {
            AuthenticatedSession session = authService.authenticate(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            attributes.put(ACCOUNT_ID_ATTRIBUTE, session.accountId());
            attributes.put(TOKEN_ATTRIBUTE, session.token());
            return true;
        } catch (ApiException e) {
            log.info("[Realtime] handshake rejected status={} code={}", e.getStatus().value(), e.getCode());
            writeError(response, e.getStatus(),
                    ErrorResponse.of(e.getCode(), e.getMessage(), path, e.isRetryable(), e.getErrors()));
            return false;
        } catch (DataAccessResourceFailureException e) {
            // GlobalExceptionHandler의 503과 같은 본문(핸드셰이크는 MVC 예외 처리를 거치지 않는다)
            log.error("[Realtime] handshake failed: store unavailable", e);
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE, ErrorResponse.of(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "잠시 뒤 다시 시도해 주세요.", path, HttpStatus.SERVICE_UNAVAILABLE));
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private void writeError(ServerHttpResponse response, HttpStatusCode status, ErrorResponse body) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            response.getBody().write(jsonMapper.writeValueAsBytes(body));
        } catch (IOException | RuntimeException e) {
            log.warn("[Realtime] could not write handshake error body", e);
        }
    }

}
