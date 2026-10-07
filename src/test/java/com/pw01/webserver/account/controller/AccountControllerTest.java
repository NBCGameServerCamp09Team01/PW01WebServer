package com.pw01.webserver.account.controller;

import com.pw01.webserver.MockMvcUtf8Config;
import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.account.service.AccountService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A3 메인화면 값(GET /accounts/me) 웹 계층 테스트. 인증 판단(AuthService)과 조회(AccountService)는 목으로 둔다.
 * 응답 칸 이름·타입(accountId는 문자열, investedStats는 객체, unlockedSkills는 배열)이 UE와 맞는지,
 * 인증 실패면 조회까지 가지 않는지 본다. 실패하면 AccountController 경로·AccountSnapshotResponse 칸 이름을 의심한다.
 */
@WebMvcTest(controllers = AccountController.class)
@ActiveProfiles("test")
@Import(MockMvcUtf8Config.class)
class AccountControllerTest {

    private static final String HEADER = "Bearer test-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    AccountService accountService;

    // 확인: 인증을 통과한 계정의 메인화면 값이 그대로 나간다(계정 ID는 인터셉터가 준 값)
    @Test
    void 내_메인화면_값() throws Exception {
        when(authService.authenticate(HEADER)).thenReturn(
                new AuthenticatedSession("test-token", 7L, Instant.parse("2026-10-07T10:10:00Z")));
        when(accountService.getSnapshot(7L)).thenReturn(
                new AccountSnapshotResponse("7", 3L, 3, 40, 2, Map.of(), List.of()));

        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("7"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.accountLevel").value(3))
                .andExpect(jsonPath("$.experience").value(40))
                .andExpect(jsonPath("$.statPoints").value(2))
                .andExpect(jsonPath("$.investedStats").isMap())
                .andExpect(jsonPath("$.unlockedSkills").isArray());
    }

    // 확인: 토큰이 없으면 401 AUTH_TOKEN_MISSING이고 DB 조회를 하지 않는다
    @Test
    void 토큰이_없으면_401_조회하지_않음() throws Exception {
        when(authService.authenticate(any())).thenThrow(
                new UnauthorizedException("AUTH_TOKEN_MISSING", "로그인이 필요합니다."));

        mockMvc.perform(get("/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.path").value("/accounts/me"));

        verify(accountService, never()).getSnapshot(anyLong());
    }

}
