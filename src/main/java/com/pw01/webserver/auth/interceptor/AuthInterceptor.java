package com.pw01.webserver.auth.interceptor;

import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 인증이 필요한 경로(WebConfig에 등록)에서 컨트롤러보다 먼저 세션을 확인한다.
 * 판단은 AuthService.authenticate가 하고, 여기서는 헤더를 넘기고 결과를 요청에 붙이기만 한다.
 * 실패하면 401 예외가 그대로 나가고 GlobalExceptionHandler가 오류 본문을 만든다.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 통과한 요청에 붙이는 로그인 정보의 이름. LoginAccountArgumentResolver가 꺼낸다 */
    static final String SESSION_ATTRIBUTE = AuthInterceptor.class.getName() + ".session";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthenticatedSession session = authService.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION));
        request.setAttribute(SESSION_ATTRIBUTE, session);
        return true;
    }

}
