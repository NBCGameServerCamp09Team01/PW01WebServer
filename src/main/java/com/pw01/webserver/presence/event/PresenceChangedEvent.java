package com.pw01.webserver.presence.event;

/**
 * 그 계정의 접속 상태가 바뀌었다: 접속하지 않은 상태 → 접속(키가 새로 생김), 또는 접속 → 끊김(이 서버가 키를 지움).
 * 이미 접속한 상태에서 다시 붙거나(다른 서버로 옮김 포함) Pong으로 수명만 채운 것은 바뀐 것이 아니라 내지 않는다.
 * PresenceService가 연결 처리 스레드에서 바로 낸다. 받는 쪽(길드 접속 알림 등)은 예외를 밖으로 던지지 않는다.
 *
 * @param accountId 계정 ID
 * @param online    true면 접속함, false면 끊김
 */
public record PresenceChangedEvent(Long accountId, boolean online) {
}
