package com.pw01.webserver.common.web;

import com.pw01.webserver.common.server.ServerId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 모든 응답에 이 서버 번호를 응답 헤더 X-Server-Id로 붙이고, 로그 MDC(serverId)에 넣는다(판 12 W5, 결정 M-4).
 * 서버가 여러 대(로드 밸런서 뒤)일 때 요청 번호(X-Request-Id)만으로는 어느 서버 로그를 볼지 모르기 때문이다
 * (루트 docs/flows/ops-health.md "서버 번호로 로그 찾기"). 오류 응답에도 붙도록 처리 전에 넣는다.
 * 값은 뜰 때 정하는 무작위 8자라 비밀이 아니고, 서버가 다시 뜨면 바뀐다.
 * <p>
 * 빈 등록은 config/ServerIdConfig가 한다(@Component가 아님). @WebMvcTest 슬라이스는 @Component 필터를 모두 싣는데
 * 이 필터가 필요로 하는 ServerId 빈은 슬라이스에 없기 때문이다. 슬라이스는 컨트롤러만 보고, 서버 번호는 앱 전체 시험이 본다.
 */
public class ServerIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Server-Id";
    private static final String MDC_KEY = "serverId";

    private final ServerId serverId;

    public ServerIdFilter(ServerId serverId) {
        this.serverId = serverId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, serverId.value());
        MDC.put(MDC_KEY, serverId.value());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

}
