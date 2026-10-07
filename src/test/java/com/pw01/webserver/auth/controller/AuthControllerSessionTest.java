package com.pw01.webserver.auth.controller;

import com.pw01.webserver.MockMvcUtf8Config;
import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.service.AuthService;
import com.pw01.webserver.common.error.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A4 접속 점검(POST /auth/heartbeat)·A5 로그아웃(POST /auth/logout) 웹 계층 테스트. AuthService는 목으로 둔다.
 * 둘 다 인터셉터를 먼저 거치는지, 상태 코드·본문 모양이 기획대로인지 본다.
 * 실패하면 AuthController의 두 메서드와 WebConfig 경로 목록을 의심한다.
 */
@WebMvcTest(controllers = AuthController.class)
@ActiveProfiles("test")
@Import(MockMvcUtf8Config.class)
class AuthControllerSessionTest {

    private static final String HEADER = "Bearer test-token";
    /** UTC ISO-8601, 초 아래는 밀리초까지(0이면 생략), 끝에 Z */
    private static final String UTC_MILLIS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z";
    private static final AuthenticatedSession SESSION =
            new AuthenticatedSession("test-token", 7L, Instant.parse("2026-10-07T10:10:00.123Z"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    // 확인: 점검은 200 + 늘어난 만료 시각(sessionExpiresAt, UTC Z)
    @Test
    void 접속_점검은_늘어난_만료시각() throws Exception {
        when(authService.authenticate(HEADER)).thenReturn(SESSION);

        mockMvc.perform(post("/auth/heartbeat").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionExpiresAt").value("2026-10-07T10:10:00.123Z"))
                .andExpect(jsonPath("$.sessionExpiresAt").value(matchesPattern(UTC_MILLIS)));
    }

    // 확인: 로그아웃은 204 본문 없음, 인터셉터가 준 세션 그대로 서비스에 넘긴다
    @Test
    void 로그아웃은_204() throws Exception {
        when(authService.authenticate(HEADER)).thenReturn(SESSION);

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authService).logout(SESSION);
    }

    // 확인: 다른 곳에서 로그인된 이전 기기의 로그아웃은 인터셉터에서 401로 끝나고 삭제까지 가지 않는다(새 기기 세션 보호)
    @Test
    void 다른_곳_로그인_뒤_로그아웃은_401_삭제_없음() throws Exception {
        when(authService.authenticate(any())).thenThrow(
                new UnauthorizedException("AUTH_SESSION_REPLACED", "다른 곳에서 로그인되었습니다."));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_REPLACED"));

        verify(authService, never()).logout(any());
    }

}
