package com.pw01.webserver.realtime.event;

/**
 * 그 계정의 연결이 살아 있다: Pong을 받아 세션을 연장했다(Ping 간격마다, 기본 60초).
 * 접속 상태를 "마지막 확인 시각"으로 둘 때 갱신에 쓴다. 같은 스레드에서 바로 불리므로 받는 쪽은 예외를 밖으로 던지지 않는다.
 *
 * @param accountId 계정 ID
 */
public record RealtimeAliveEvent(Long accountId) {
}
