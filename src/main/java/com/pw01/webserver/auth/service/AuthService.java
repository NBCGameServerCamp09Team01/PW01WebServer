package com.pw01.webserver.auth.service;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.account.dto.LoginCandidate;
import com.pw01.webserver.account.entity.Account;
import com.pw01.webserver.account.entity.AccountStatus;
import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.auth.dto.AuthenticatedSession;
import com.pw01.webserver.auth.dto.LoginRequest;
import com.pw01.webserver.auth.dto.LoginResponse;
import com.pw01.webserver.auth.dto.SignupRequest;
import com.pw01.webserver.auth.dto.SignupResponse;
import com.pw01.webserver.auth.repository.IssuedSession;
import com.pw01.webserver.auth.repository.LoginFailResult;
import com.pw01.webserver.auth.repository.LoginFailStore;
import com.pw01.webserver.auth.repository.SessionCheck;
import com.pw01.webserver.auth.repository.SessionStore;
import com.pw01.webserver.common.error.ForbiddenException;
import com.pw01.webserver.common.error.TooManyRequestsException;
import com.pw01.webserver.common.error.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 회원가입·로그인 흐름(유스케이스). 계정 데이터는 AccountService로만 다룬다.
 * 비밀번호 해시·비교는 느린 계산이라 트랜잭션 밖에서 한다(DB 읽기·쓰기 트랜잭션은 AccountService가 가진다).
 * 오류 코드는 기획 first-flow-plan(이후 루트 docs/contracts/auth-api.md) 오류 표를 따른다.
 */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    static final String LOGIN_LOCKED = "AUTH_LOGIN_LOCKED";
    static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";
    static final String TOKEN_MISSING = "AUTH_TOKEN_MISSING";
    static final String TOKEN_INVALID = "AUTH_TOKEN_INVALID";
    static final String SESSION_NOT_FOUND = "AUTH_SESSION_NOT_FOUND";
    static final String SESSION_REPLACED = "AUTH_SESSION_REPLACED";

    private static final String BEARER_PREFIX = "Bearer ";
    /** 32바이트를 URL-safe Base64(패딩 없음)로 쓴 43자. 모양이 틀리면 Redis를 보지 않는다 */
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    /** 가입 규칙과 같다. 이 형식이 아니면 DB·Redis를 보지 않고 같은 401(회의 10/7, 회신 1-1의 6) */
    private static final Pattern LOGIN_ID_FORMAT = Pattern.compile("^[A-Za-z0-9]{4,20}$");
    /** BCrypt는 이보다 긴 비밀번호에 예외를 던진다. 그런 비밀번호로는 가입할 수 없으니 틀린 것으로 본다 */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final LoginFailStore loginFailStore;
    /** 없는 아이디일 때도 해시 비교를 한 번 해서 응답 시간으로 아이디가 있는지 짐작하기 어렵게 한다 */
    private final String dummyHash;

    public AuthService(AccountService accountService, PasswordEncoder passwordEncoder,
                       SessionStore sessionStore, LoginFailStore loginFailStore) {
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.sessionStore = sessionStore;
        this.loginFailStore = loginFailStore;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public SignupResponse signup(SignupRequest request) {
        String passwordHash = passwordEncoder.encode(request.password());
        Account account = accountService.register(
                request.loginId(), passwordHash, request.nickname(), request.email());
        return SignupResponse.from(account);
    }

    /**
     * 판단 순서가 규칙이다: ① 형식 밖 아이디 401 → ② 잠김 429(비밀번호를 보지 않음)
     * → ③④ 계정 없음·비밀번호 틀림은 실패 기록 후 401(5번째는 429) → ⑤ 제재 403(비밀번호가 맞은 뒤에만)
     * → ⑥ 성공: 메인화면 값 → 실패 기록 삭제 → 세션 생성
     * 메인화면 값을 세션보다 먼저 읽는다: 읽기가 실패하면(진행 행 없음 500, DB 장애 503) 세션을 만들지 않는다.
     */
    public LoginResponse login(LoginRequest request) {
        String loginId = request.loginId();

        if (!LOGIN_ID_FORMAT.matcher(loginId).matches()) {
            throw invalidCredentials();
        }

        long lockRemaining = loginFailStore.lockRemaining(loginId);
        if (lockRemaining > 0) {
            throw locked(lockRemaining);
        }

        LoginCandidate candidate = accountService.findLoginCandidate(loginId).orElse(null);
        String hashToCheck = candidate != null ? candidate.passwordHash() : dummyHash;
        boolean passwordMatches = passwordMatches(request.password(), hashToCheck);
        if (candidate == null || !passwordMatches) {
            throw recordFailureAndReject(loginId);
        }

        if (candidate.status() == AccountStatus.SUSPENDED) {
            throw new ForbiddenException(ACCOUNT_SUSPENDED, "이용이 제한된 계정입니다.");
        }

        AccountSnapshotResponse snapshot = accountService.getSnapshot(candidate.accountId());
        loginFailStore.clear(loginId);
        IssuedSession session = sessionStore.create(candidate.accountId());
        return LoginResponse.of(session, snapshot);
    }

    /**
     * 인증이 필요한 요청마다(인터셉터가 부름). 확인 순서가 규칙이다:
     * ① Authorization 헤더가 "Bearer "로 시작 → ② 토큰 모양 → ③ 세션이 있음 → ④ 지금 세션이 내 토큰. 통과하면 세션이 연장된다.
     *
     * @param authorizationHeader Authorization 헤더 값(없으면 null)
     */
    public AuthenticatedSession authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException(TOKEN_MISSING, "로그인이 필요합니다.");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!TOKEN_FORMAT.matcher(token).matches()) {
            throw new UnauthorizedException(TOKEN_INVALID, "인증 정보가 올바르지 않습니다.");
        }

        SessionCheck check = sessionStore.touch(token);
        return switch (check.status()) {
            case OK -> new AuthenticatedSession(token, check.accountId(), check.expiresAt());
            case NOT_FOUND -> throw new UnauthorizedException(SESSION_NOT_FOUND, "세션이 끝났습니다. 다시 로그인해 주세요.");
            case REPLACED -> throw new UnauthorizedException(SESSION_REPLACED, "다른 곳에서 로그인되었습니다.");
        };
    }

    /**
     * 로그아웃. 인터셉터를 통과한 세션만 온다(다른 곳에서 로그인된 이전 기기는 인터셉터에서 이미 401).
     * 계정 키는 지금 값이 내 토큰일 때만 지우므로, 그사이 새 기기가 로그인했어도 새 세션은 남는다.
     */
    public void logout(AuthenticatedSession session) {
        sessionStore.delete(session.token(), session.accountId());
    }

    private boolean passwordMatches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private RuntimeException recordFailureAndReject(String loginId) {
        LoginFailResult result = loginFailStore.recordFailure(loginId);
        if (result.locked()) {
            return locked(result.lockRemainingSeconds());
        }
        return invalidCredentials();
    }

    /** 아이디 없음·비밀번호 틀림·형식 밖 아이디가 모두 같은 코드·같은 문구(아이디가 있는지 밖에서 알 수 없게) */
    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(INVALID_CREDENTIALS, "아이디 또는 비밀번호가 맞지 않습니다.");
    }

    private static TooManyRequestsException locked(long seconds) {
        return new TooManyRequestsException(LOGIN_LOCKED, "로그인 시도가 너무 많습니다. 잠시 뒤 다시 시도해 주세요.", seconds);
    }

}
