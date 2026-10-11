package com.pw01.webserver.auth.event;

/**
 * 로그인(A2)으로 새 세션이 생겼다. 계정당 세션은 하나이므로, 이 계정의 이전 세션은 이 순간 "다른 곳 로그인" 상태가 된다.
 * 실시간 연결(realtime)이 받아 이전 세션의 연결에 알리고 닫는다(루트 docs/contracts/realtime-api.md "같은 계정의 연결이 겹칠 때").
 * 받는 쪽이 실패해도 로그인은 성공해야 하므로, 받는 쪽은 예외를 밖으로 던지지 않는다.
 *
 * @param accountId 새 세션의 계정 ID
 */
public record SessionIssuedEvent(Long accountId) {
}
