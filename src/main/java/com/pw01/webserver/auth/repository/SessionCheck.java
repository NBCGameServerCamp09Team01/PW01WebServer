package com.pw01.webserver.auth.repository;

import java.time.Instant;

/**
 * 세션 확인·연장 결과(SessionStore.touch). 루트 docs/contracts/redis-keys.md v1 session_extend.lua의 돌려줌 값과 같다.
 * OK일 때만 accountId·expiresAt이 있다(늘린 뒤의 만료 시각).
 */
public record SessionCheck(Status status, Long accountId, Instant expiresAt) {

    public enum Status {
        /** 세션이 있고 내 토큰이 지금 세션이다. 두 키를 세션 수명으로 다시 걸었다 (스크립트 1) */
        OK,
        /** 세션 키나 계정 키가 없다: 로그아웃·만료·없는 토큰 (스크립트 0, 또는 세션 키 GET이 없음) */
        NOT_FOUND,
        /** 같은 계정이 다른 곳에서 새로 로그인했다 (스크립트 2) */
        REPLACED
    }

    public static SessionCheck ok(Long accountId, Instant expiresAt) {
        return new SessionCheck(Status.OK, accountId, expiresAt);
    }

    public static SessionCheck notFound() {
        return new SessionCheck(Status.NOT_FOUND, null, null);
    }

    public static SessionCheck replaced() {
        return new SessionCheck(Status.REPLACED, null, null);
    }

}
