package com.pw01.webserver.auth.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.repository.LoginFailStore;
import com.pw01.webserver.auth.repository.SessionCheck;
import com.pw01.webserver.auth.repository.SessionStore;
import com.pw01.webserver.common.error.ApiException;
import com.pw01.webserver.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 인증 확인 순서(헤더 → 토큰 모양 → 세션 → 다른 곳 로그인) 단위 테스트. Redis 없이 SessionStore를 목으로 바꾼다.
 * 실패하면 AuthService.authenticate의 확인 순서나 401 코드 4종이 바뀌었는지 먼저 의심한다.
 */
class AuthServiceAuthenticateTest {

    /** 43자 URL-safe Base64(32바이트) 모양의 토큰 */
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    private SessionStore sessionStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        sessionStore = mock(SessionStore.class);
        authService = new AuthService(mock(AccountService.class), new BCryptPasswordEncoder(4),
                sessionStore, mock(LoginFailStore.class));
    }

    // 확인: 헤더가 없거나 Bearer가 아니면 AUTH_TOKEN_MISSING, Redis는 보지 않는다
    @Test
    void 헤더가_없거나_Bearer가_아니면_TOKEN_MISSING() {
        assertCode(() -> authService.authenticate(null), AuthService.TOKEN_MISSING);
        assertCode(() -> authService.authenticate("Basic " + TOKEN), AuthService.TOKEN_MISSING);
        assertCode(() -> authService.authenticate(TOKEN), AuthService.TOKEN_MISSING);
        verifyNoInteractions(sessionStore);
    }

    // 확인: 길이·문자가 틀린 토큰은 AUTH_TOKEN_INVALID, Redis는 보지 않는다(키 길이를 묶음)
    @Test
    void 모양이_틀린_토큰은_TOKEN_INVALID() {
        assertCode(() -> authService.authenticate("Bearer "), AuthService.TOKEN_INVALID);
        assertCode(() -> authService.authenticate("Bearer " + TOKEN.substring(1)), AuthService.TOKEN_INVALID);
        assertCode(() -> authService.authenticate("Bearer " + TOKEN + "x"), AuthService.TOKEN_INVALID);
        assertCode(() -> authService.authenticate("Bearer " + TOKEN.replace('a', '+')), AuthService.TOKEN_INVALID);
        verifyNoInteractions(sessionStore);
    }

    // 확인: 저장소가 세션 없음을 알려 주면 AUTH_SESSION_NOT_FOUND(로그아웃·만료)
    @Test
    void 세션이_없으면_SESSION_NOT_FOUND() {
        when(sessionStore.touch(TOKEN)).thenReturn(SessionCheck.notFound());
        assertCode(() -> authService.authenticate("Bearer " + TOKEN), AuthService.SESSION_NOT_FOUND);
    }

    // 확인: 다른 곳에서 로그인했으면 AUTH_SESSION_REPLACED(게임은 안내 후 타이틀)
    @Test
    void 다른_곳에서_로그인했으면_SESSION_REPLACED() {
        when(sessionStore.touch(TOKEN)).thenReturn(SessionCheck.replaced());
        assertCode(() -> authService.authenticate("Bearer " + TOKEN), AuthService.SESSION_REPLACED);
    }

    // 확인: 통과하면 계정 ID와 늘어난 만료 시각을 돌려준다
    @Test
    void 통과하면_계정과_만료시각() {
        Instant expiresAt = Instant.parse("2026-10-07T10:10:00Z");
        when(sessionStore.touch(TOKEN)).thenReturn(SessionCheck.ok(7L, expiresAt));

        AuthenticatedSession session = authService.authenticate("Bearer " + TOKEN);

        assertThat(session.accountId()).isEqualTo(7L);
        assertThat(session.expiresAt()).isEqualTo(expiresAt);
        assertThat(session.token()).isEqualTo(TOKEN);
        assertThat(session.toString()).doesNotContain(TOKEN);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(code);
    }

}
