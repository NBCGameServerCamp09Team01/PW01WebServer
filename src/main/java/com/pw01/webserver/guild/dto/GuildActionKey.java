package com.pw01.webserver.guild.dto;

/**
 * 멱등 처리(IdempotencyService)에 넘기는 "요청 내용". 본문({requestId})만 해시하면 다른 신청을 같은 requestId로 수락할 때
 * 같은 요청으로 오인해 처음 응답을 돌려준다. 동작 이름과 경로의 대상 ID를 함께 담아, 다르면 409 IDEMPOTENCY_KEY_REUSED가 되게 한다.
 *
 * @param action   approve·reject·leave·disband
 * @param targetId 경로의 대상(신청 ID). 대상이 없는 동작은 null
 */
public record GuildActionKey(String action, String targetId, String requestId) {
}
