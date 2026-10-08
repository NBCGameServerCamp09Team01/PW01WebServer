package com.pw01.webserver.auth.interceptor;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인터셉터 연결 테스트: 등록한 경로에만 걸리는가, 401이 공통 오류 본문으로 나가는가, @LoginAccount가 채워지는가.
 * 판단 로직은 AuthServiceAuthenticateTest가 보므로 여기서는 AuthService를 목으로 둔다(DB·Redis·Docker 불필요).
 * 실패하면 WebConfig의 경로 목록과 LoginAccountArgumentResolver를 의심한다.
 */
@WebMvcTest(controllers = AuthInterceptorTest.SecuredTestController.class)
@ActiveProfiles("test")
@Import({AuthInterceptorTest.SecuredTestController.class, MockMvcUtf8Config.class})
class AuthInterceptorTest {

    private static final String HEADER = "Bearer test-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    // 확인: 인증 경로에서 401 예외가 나면 컨트롤러까지 가지 않고 공통 오류 본문 {code, path}로 나간다
    @Test
    void 인증_실패는_401_오류_본문() throws Exception {
        when(authService.authenticate(any())).thenThrow(
                new UnauthorizedException("AUTH_SESSION_REPLACED", "다른 곳에서 로그인되었습니다."));

        mockMvc.perform(get("/accounts/test-id").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_REPLACED"))
                .andExpect(jsonPath("$.path").value("/accounts/test-id"));
    }

    // 확인: 통과하면 @LoginAccount Long에 계정 ID, AuthenticatedSession에 만료 시각까지 들어간다
    @Test
    void 통과하면_LoginAccount가_채워진다() throws Exception {
        when(authService.authenticate(HEADER)).thenReturn(
                new AuthenticatedSession("test-token", 7L, Instant.parse("2026-10-07T10:10:00Z")));

        mockMvc.perform(get("/accounts/test-id").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7)); // 성공 본문은 {data, meta}로 감싼다(ApiResponseAdvice)

        mockMvc.perform(post("/auth/heartbeat").header(HttpHeaders.AUTHORIZATION, HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("2026-10-07T10:10:00Z"));
    }

    // 확인: 공개 경로(PublicPaths, 예: 가입)에는 인터셉터가 걸리지 않는다. 실패하면 WebConfig의 excludePathPatterns를 본다
    @Test
    void 공개_경로는_인증하지_않는다() throws Exception {
        mockMvc.perform(post("/auth/signup"))
                .andExpect(status().isOk());

        verify(authService, never()).authenticate(any());
    }

    // 확인: 공개 목록에 없는 경로는 모두 인증을 거친다("/**", SF-2). 새 인증 API가 WebConfig를 고치지 않아도 막힌다
    @Test
    void 목록에_없는_경로는_모두_인증() throws Exception {
        when(authService.authenticate(any())).thenThrow(
                new UnauthorizedException("AUTH_TOKEN_MISSING", "로그인이 필요합니다."));

        mockMvc.perform(post("/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    /** 테스트 전용 컨트롤러. 인증 경로(/accounts/**, /auth/heartbeat)와 인증 없는 경로를 하나씩 둔다 */
    @RestController
    static class SecuredTestController {

        @GetMapping("/accounts/test-id")
        Long accountId(@LoginAccount Long accountId) {
            return accountId;
        }

        @PostMapping("/auth/heartbeat")
        String heartbeat(@LoginAccount AuthenticatedSession session) {
            return session.expiresAt().toString();
        }

        @PostMapping("/auth/signup")
        void open() {
        }

        @PostMapping("/runs")
        void secured() {
        }

    }

}
