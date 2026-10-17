package com.pw01.webserver.chat.dto;

import com.pw01.webserver.common.chat.ChatScope;

/**
 * 멱등 처리(IdempotencyService)에 넘기는 "요청 내용". 본문만 해시하면 같은 requestId·같은 글을 전체와 길드에 보낼 때
 * 같은 요청으로 오인해 처음 응답을 돌려준다. 어디로 보내는지(scope)를 함께 담아, 다르면 409 IDEMPOTENCY_KEY_REUSED가 되게 한다(GuildActionKey와 같은 결).
 *
 * @param scope GLOBAL(CH1) · GUILD(CH2)
 * @param text  요청에 온 글 그대로(자르기 전)
 */
public record ChatSendKey(ChatScope scope, String text) {
}
