package com.pw01.webserver.stage.controller;

import com.pw01.webserver.MockMvcUtf8Config;
import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.service.AuthService;
import com.pw01.webserver.common.error.UnauthorizedException;
import com.pw01.webserver.stage.dto.StageProgressListResponse;
import com.pw01.webserver.stage.dto.StageProgressResponse;
import com.pw01.webserver.stage.service.StageProgressService;
import com.pw01.webserver.stage.service.StageStatus;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ST2 내 스테이지 진행(GET /accounts/me/stages) 웹 계층 테스트. 인증 판단과 조회는 목으로 둔다.
 * 칸 이름·타입(status는 문자열, 시각은 Z가 붙은 밀리초, 없으면 null)과, 인증 실패면 조회까지 가지 않는지 본다.
 * 실패하면 MyStageProgressController 경로, DTO 칸 이름, WebConfig의 /accounts/** 를 의심한다.
 */
@WebMvcTest(controllers = MyStageProgressController.class)
@ActiveProfiles("test")
@Import(MockMvcUtf8Config.class)
class MyStageProgressControllerTest {

    private static final String HEADER = "Bearer test-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    StageProgressService stageProgressService;

    // 확인: 인증한 계정의 진행이 명세 모양 그대로 나간다
    @Test
    void 내_진행() throws Exception {
        when(authService.authenticate(HEADER)).thenReturn(
                new AuthenticatedSession("test-token", 7L, Instant.parse("2026-10-08T10:10:00Z")));
        when(stageProgressService.getMyProgress(7L)).thenReturn(new StageProgressListResponse(1, 2, List.of(
                new StageProgressResponse("stage.01.01", StageStatus.CLEARED, Instant.parse("2026-10-08T03:00:00.123Z")),
                new StageProgressResponse("stage.01.02", StageStatus.OPEN, null))));

        mockMvc.perform(get("/accounts/me/stages").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clearedCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.stages[0].stageId").value("stage.01.01"))
                .andExpect(jsonPath("$.data.stages[0].status").value("CLEARED"))
                .andExpect(jsonPath("$.data.stages[0].firstClearedAt").value("2026-10-08T03:00:00.123Z"))
                .andExpect(jsonPath("$.data.stages[1].status").value("OPEN"))
                .andExpect(content().string(containsString("\"firstClearedAt\":null")));
    }

    // 확인: 토큰이 없으면 401 AUTH_TOKEN_MISSING이고 조회를 하지 않는다
    @Test
    void 토큰이_없으면_401_조회하지_않음() throws Exception {
        when(authService.authenticate(any())).thenThrow(
                new UnauthorizedException("AUTH_TOKEN_MISSING", "로그인이 필요합니다."));

        mockMvc.perform(get("/accounts/me/stages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.path").value("/accounts/me/stages"));

        verify(stageProgressService, never()).getMyProgress(anyLong());
    }

}
