package com.pw01.webserver.realtime.service;

import org.springframework.web.socket.CloseStatus;

/**
 * 서버가 연결을 닫을 때 쓰는 종료 코드. 루트 docs/contracts/realtime-api.md "종료 코드" 표와 같다.
 * 게임은 숫자로 판단하고, 이유 문자열(WS_ 코드)은 로그용이다.
 */
public final class RealtimeCloseStatus {

    /** 1000: 로그아웃(A5) */
    public static final CloseStatus LOGGED_OUT = CloseStatus.NORMAL;

    /** 4000: 같은 세션의 새 연결이 생김(보통 게임 자신의 재연결). 게임은 다시 연결하지 않는다 */
    public static final CloseStatus CONNECTION_REPLACED = new CloseStatus(4000, "WS_CONNECTION_REPLACED");

    /** 4001: 다른 곳 로그인. 바로 앞에 SESSION_REPLACED 메시지를 보낸다 */
    public static final CloseStatus SESSION_REPLACED = new CloseStatus(4001, "WS_SESSION_REPLACED");

    /** 4002: 세션이 없음(만료·로그아웃·제재로 끊김) */
    public static final CloseStatus SESSION_ENDED = new CloseStatus(4002, "WS_SESSION_ENDED");

    /** 4003: 연속으로 Pong이 없음. 게임은 다시 연결한다 */
    public static final CloseStatus HEARTBEAT_TIMEOUT = new CloseStatus(4003, "WS_HEARTBEAT_TIMEOUT");

    private RealtimeCloseStatus() {
    }

}
