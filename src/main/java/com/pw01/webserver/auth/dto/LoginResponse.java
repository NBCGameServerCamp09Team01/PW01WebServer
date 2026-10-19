package com.pw01.webserver.auth.dto;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.auth.repository.IssuedSession;

import java.time.Instant;

/**
 * 로그인 응답: 세션 토큰 + 메인화면 값.
 * sessionExpiresAt은 접속 점검(A4) 응답과 같은 이름이다(같은 값, part d 회신 3-1의 8).
 */
public record LoginResponse(String accessToken, String tokenType, Instant sessionExpiresAt,
                            AccountSnapshotResponse account) {

    private static final String BEARER = "Bearer";

    public static LoginResponse of(IssuedSession session, AccountSnapshotResponse account) {
        return new LoginResponse(session.token(), BEARER, session.expiresAt(), account);
    }

}
