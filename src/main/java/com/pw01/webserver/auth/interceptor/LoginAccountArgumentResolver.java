package com.pw01.webserver.auth.interceptor;

import com.pw01.webserver.auth.dto.AuthenticatedSession;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @LoginAccount 인자를 채운다. 인터셉터가 요청에 붙인 AuthenticatedSession을 꺼낸다.
 * 값이 없으면 인터셉터가 걸리지 않은 경로에 @LoginAccount를 쓴 것이라 코드 실수(500)다.
 */
public class LoginAccountArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return parameter.hasParameterAnnotation(LoginAccount.class)
                && (type == Long.class || type == AuthenticatedSession.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object value = webRequest.getAttribute(AuthInterceptor.SESSION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthenticatedSession session)) {
            throw new IllegalStateException("인증 인터셉터가 걸리지 않은 경로에서 @LoginAccount를 썼습니다: "
                    + parameter.getExecutable().getName());
        }
        return parameter.getParameterType() == Long.class ? session.accountId() : session;
    }

}
