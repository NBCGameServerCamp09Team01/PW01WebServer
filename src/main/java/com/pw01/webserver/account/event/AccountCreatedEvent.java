package com.pw01.webserver.account.event;

/**
 * 계정이 새로 만들어졌다(가입). AccountService.register가 가입 트랜잭션 안에서 낸다.
 * 계정에 딸린 다른 기능의 첫 행(예: 길드 소속 행)을 계정 기능이 그 기능을 몰라도 만들 수 있게 한다(계정 → 길드 의존 없음).
 * 받는 쪽이 @EventListener(같은 트랜잭션)로 받으면 받는 쪽 실패는 가입도 되돌린다. 커밋 뒤 일이면 @TransactionalEventListener로 받는다.
 *
 * @param accountId 새 계정 ID
 */
public record AccountCreatedEvent(Long accountId) {
}
