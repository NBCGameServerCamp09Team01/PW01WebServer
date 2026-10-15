package com.pw01.webserver.realtime.event;

/**
 * 이 서버의 연결 목록에서 그 계정의 연결이 빠졌다(닫힘). 같은 세션의 새 연결로 바뀌어 닫힌 이전 연결(4000)은 목록에 없으므로 내지 않는다.
 * 같은 스레드에서 바로 불리므로 받는 쪽은 예외를 밖으로 던지지 않는다.
 * 서버가 갑자기 꺼지면 이 이벤트가 나지 않는다 → 접속 상태는 마지막 확인 시각(RealtimeAliveEvent)으로도 정리해야 한다.
 *
 * @param accountId 계정 ID
 * @param serverId  연결이 붙어 있던 서버 번호
 * @param closeCode WebSocket 종료 코드(realtime-api.md "종료 코드")
 */
public record RealtimeDisconnectedEvent(Long accountId, String serverId, int closeCode) {
}
