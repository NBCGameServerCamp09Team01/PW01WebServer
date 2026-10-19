package com.pw01.webserver.chat.dto;

import com.pw01.webserver.common.chat.ChatMessage;

import java.time.Instant;

/**
 * 채팅 한 줄 응답(루트 docs/contracts/chat-api.md "채팅 한 줄"). CH1·CH2 응답 data, CH3 messages의 한 칸.
 * 실시간 CHAT_MESSAGE의 data(ChatMessage.toRealtimeData)와 같은 칸·같은 이름이다. guildId는 전체 채팅이면 null로 나간다(빼지 않음).
 */
public record ChatMessageResponse(String messageId, String scope, String guildId,
                                  String senderAccountId, String senderNickname,
                                  String text, Instant sentAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.messageId(), message.scope().name(), message.guildId(),
                message.senderAccountId(), message.senderNickname(), message.text(), message.sentAt());
    }

}
