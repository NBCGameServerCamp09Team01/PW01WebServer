package com.pw01.webserver.auth.repository;

import java.time.Instant;

/** 만든 세션: 토큰 원문(게임에 한 번만 준다)과 세션 만료 시각(UTC) */
public record IssuedSession(String token, Instant expiresAt) {
}
