package com.pw01.webserver.auth.dto;

import java.time.Instant;

/**
 * 접속 점검(A4) 응답. 임시 API: UE WebSocket이 붙으면 Ping/Pong으로 옮기고 지운다.
 * 이름은 로그인 응답의 sessionExpiresAt과 같다(같은 뜻의 값).
 *
 * @param sessionExpiresAt 이번 점검으로 늘어난 세션 만료 시각
 */
public record HeartbeatResponse(Instant sessionExpiresAt) {

    public static HeartbeatResponse from(AuthenticatedSession session) {
        return new HeartbeatResponse(session.expiresAt());
    }

}
