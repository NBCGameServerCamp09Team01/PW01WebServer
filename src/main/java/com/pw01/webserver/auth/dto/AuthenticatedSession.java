package com.pw01.webserver.auth.dto;

import java.time.Instant;

/**
 * 인터셉터를 통과한 요청의 로그인 정보. API로 내보내지 않는 서버 내부 값이다.
 * 컨트롤러는 @LoginAccount로 받는다(계정 ID만 필요하면 Long, 만료 시각도 필요하면 이 타입).
 *
 * @param token     요청에 실린 토큰 원문(로그아웃 때 저장소에 넘김). 로그에 남기지 않는다
 * @param accountId 계정 ID
 * @param expiresAt 이번 요청으로 늘어난 세션 만료 시각(접속 점검 응답의 sessionExpiresAt)
 */
public record AuthenticatedSession(String token, Long accountId, Instant expiresAt) {

    /** 토큰 원문이 로그·예외 메시지에 찍히지 않게 가린다 */
    @Override
    public String toString() {
        return "AuthenticatedSession[accountId=" + accountId + ", expiresAt=" + expiresAt + "]";
    }

}
