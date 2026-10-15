package com.pw01.webserver.realtime.event;

/**
 * 이 서버에 실시간 연결이 등록됐다. 접속 상태(M23 소셜 현황)를 만드는 기능이 받는다.
 * 같은 스레드에서 바로 불리므로 받는 쪽은 예외를 밖으로 던지지 않는다(던져도 연결은 유지되고 경고 로그만 남는다).
 *
 * @param accountId 계정 ID
 * @param serverId  연결이 붙은 서버 번호(RealtimeServerId)
 */
public record RealtimeConnectedEvent(Long accountId, String serverId) {
}
