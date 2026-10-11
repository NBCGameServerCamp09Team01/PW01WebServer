package com.pw01.webserver.auth.event;

/**
 * 로그아웃(A5)으로 세션이 끝났다. 실시간 연결(realtime)이 받아 그 세션의 연결을 닫는다.
 * 받는 쪽이 실패해도 로그아웃은 성공해야 하므로, 받는 쪽은 예외를 밖으로 던지지 않는다.
 *
 * @param accountId 계정 ID
 * @param token     끝난 세션의 토큰 원문(같은 계정의 다른 세션 연결을 닫지 않으려고 비교한다). 로그에 남기지 않는다
 */
public record SessionEndedEvent(Long accountId, String token) {

    /** 토큰 원문이 로그·예외 메시지에 찍히지 않게 가린다 */
    @Override
    public String toString() {
        return "SessionEndedEvent[accountId=" + accountId + "]";
    }

}
