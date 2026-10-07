package com.pw01.webserver.auth.service;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.account.dto.LoginCandidate;
import com.pw01.webserver.account.entity.AccountStatus;
import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.auth.dto.LoginRequest;
import com.pw01.webserver.auth.dto.LoginResponse;
import com.pw01.webserver.auth.repository.IssuedSession;
import com.pw01.webserver.auth.repository.LoginFailResult;
import com.pw01.webserver.auth.repository.LoginFailStore;
import com.pw01.webserver.auth.repository.SessionStore;
import com.pw01.webserver.common.error.ApiException;
import com.pw01.webserver.common.error.ForbiddenException;
import com.pw01.webserver.common.error.TooManyRequestsException;
import com.pw01.webserver.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 로그인 판단 순서 단위 테스트. Redis·DB 없이 저장소를 목으로 바꾸고 BCrypt는 진짜를 쓴다(Docker 불필요).
 * 실패하면 AuthService.login의 판단 순서(형식 → 잠김 → 계정·비밀번호 → 제재 → 성공)가 바뀌었는지 먼저 의심한다.
 */
class AuthServiceLoginTest {

    private static final String LOGIN_ID = "warrior01";
    private static final String PASSWORD = "password123";
    private static final Long ACCOUNT_ID = 1L;

    // 비용 4: 테스트 속도용(운영 기본은 10)
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final String passwordHash = passwordEncoder.encode(PASSWORD);

    private AccountService accountService;
    private SessionStore sessionStore;
    private LoginFailStore loginFailStore;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        sessionStore = mock(SessionStore.class);
        loginFailStore = mock(LoginFailStore.class);
        authService = new AuthService(accountService, passwordEncoder, sessionStore, loginFailStore);
    }

    // 확인: 형식 밖 아이디는 DB·Redis를 보지 않고 401. 실패하면 형식 검사가 잠김 확인 뒤로 밀렸는지 본다
    @Test
    void 형식_밖_아이디는_저장소를_보지_않고_401() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("ab!", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(AuthService.INVALID_CREDENTIALS);

        verifyNoInteractions(accountService, sessionStore, loginFailStore);
    }

    // 확인: 잠긴 동안은 비밀번호를 보지 않고 429 + 남은 초. 실패하면 잠김 확인 순서를 본다
    @Test
    void 잠긴_아이디는_계정을_조회하지_않고_429() {
        when(loginFailStore.lockRemaining(LOGIN_ID)).thenReturn(42L);

        assertThatThrownBy(() -> authService.login(new LoginRequest(LOGIN_ID, PASSWORD)))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(AuthService.LOGIN_LOCKED);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(42L);
                });

        verifyNoInteractions(accountService, sessionStore);
        verify(loginFailStore, never()).recordFailure(anyString());
    }

    // 확인: 계정 없음과 비밀번호 틀림이 같은 코드·같은 문구. 실패하면 아이디 존재 여부가 밖으로 드러난다
    @Test
    void 계정_없음과_비밀번호_틀림은_같은_401() {
        when(loginFailStore.recordFailure(anyString())).thenReturn(new LoginFailResult(1, 0));

        when(accountService.findLoginCandidate(LOGIN_ID)).thenReturn(Optional.empty());
        Throwable noAccount = catchThrowable(new LoginRequest(LOGIN_ID, PASSWORD));

        when(accountService.findLoginCandidate(LOGIN_ID)).thenReturn(Optional.of(candidate(AccountStatus.ACTIVE)));
        Throwable wrongPassword = catchThrowable(new LoginRequest(LOGIN_ID, "wrong-password"));

        assertThat(noAccount).isInstanceOf(UnauthorizedException.class);
        assertThat(wrongPassword).isInstanceOf(UnauthorizedException.class);
        assertThat(((ApiException) noAccount).getCode()).isEqualTo(((ApiException) wrongPassword).getCode());
        assertThat(noAccount.getMessage()).isEqualTo(wrongPassword.getMessage());
        verify(sessionStore, never()).create(anyLong());
    }

    // 확인: 실패 기록이 잠김을 알려 주면(5번째 실패) 401이 아니라 429. 실패하면 recordFailure 결과 처리를 본다
    @Test
    void 다섯번째_실패는_429() {
        when(accountService.findLoginCandidate(LOGIN_ID)).thenReturn(Optional.of(candidate(AccountStatus.ACTIVE)));
        when(loginFailStore.recordFailure(LOGIN_ID)).thenReturn(new LoginFailResult(5, 60));

        assertThatThrownBy(() -> authService.login(new LoginRequest(LOGIN_ID, "wrong-password")))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60L));
    }

    // 확인: 72바이트를 넘는 비밀번호는 BCrypt 예외(500) 대신 틀린 비밀번호(401)로 처리된다
    @Test
    void 너무_긴_비밀번호는_401() {
        when(accountService.findLoginCandidate(LOGIN_ID)).thenReturn(Optional.of(candidate(AccountStatus.ACTIVE)));
        when(loginFailStore.recordFailure(LOGIN_ID)).thenReturn(new LoginFailResult(1, 0));

        assertThatThrownBy(() -> authService.login(new LoginRequest(LOGIN_ID, "a".repeat(73))))
                .isInstanceOf(UnauthorizedException.class);
    }

    // 확인: 제재 계정은 비밀번호가 맞을 때만 403이고 세션을 만들지 않는다
    @Test
    void 제재_계정은_비밀번호가_맞으면_403_세션_없음() {
        when(accountService.findLoginCandidate(LOGIN_ID)).thenReturn(Optional.of(candidate(AccountStatus.SUSPENDED)));

        assertThatThrownBy(() -> authService.login(new LoginRequest(LOGIN_ID, PASSWORD)))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(AuthService.ACCOUNT_SUSPENDED));

        verify(sessionStore, never()).create(any());
    }

    // 확인: 성공하면 실패 기록을 지우고 세션을 만들고 메인화면 값을 붙인다
    @Test
    void 성공하면_실패기록_삭제_세션_생성_메인화면_값() {
        Instant expiresAt = Instant.parse("2026-10-07T10:10:00Z");
        when(accountService.findLoginCandidate(LOGIN_ID)).thenReturn(Optional.of(candidate(AccountStatus.ACTIVE)));
        when(sessionStore.create(ACCOUNT_ID)).thenReturn(new IssuedSession("token-value", expiresAt));
        when(accountService.getSnapshot(ACCOUNT_ID)).thenReturn(
                new AccountSnapshotResponse("1", 0L, 1, 0, 0, Map.of(), List.of()));

        LoginResponse response = authService.login(new LoginRequest(LOGIN_ID, PASSWORD));

        verify(loginFailStore).clear(LOGIN_ID);
        assertThat(response.accessToken()).isEqualTo("token-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.sessionExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.account().accountLevel()).isEqualTo(1);
    }

    private LoginCandidate candidate(AccountStatus status) {
        return new LoginCandidate(ACCOUNT_ID, passwordHash, status);
    }

    private Throwable catchThrowable(LoginRequest request) {
        try {
            authService.login(request);
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

}
