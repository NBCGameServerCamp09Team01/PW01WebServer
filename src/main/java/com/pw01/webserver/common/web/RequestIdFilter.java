package com.pw01.webserver.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 요청 번호를 만들어 응답 헤더 X-Request-Id에 붙이고, 요청 속성과 로그 MDC(requestId)에 넣는다.
 * 성공 응답의 meta.requestId도 같은 값이다(ApiResponseAdvice). 오류 응답은 헤더로만 나간다.
 * 지금은 서버가 만든다. 상태를 바꾸는 요청에 게임이 본문 requestId(중복 방지 키)를 보내게 되면(S2) 그 값으로 바꾼다
 * (루트 docs/contracts/README.md "성공 응답").
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    /** 요청 속성 이름. requestId(HttpServletRequest)로 읽는다 */
    static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 이 요청의 요청 번호. 필터를 거치지 않은 경우(일부 슬라이스 시험)에는 null */
    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof String id ? id : null;
    }

}
