package com.pw01.webserver.chat.dto;

import com.pw01.webserver.common.chat.ChatHistoryPage;

import java.util.List;

/**
 * CH3 길드 채팅 내역 응답(루트 docs/contracts/chat-api.md).
 *
 * @param messages   새것 → 옛것 순
 * @param nextCursor 더 옛 묶음을 받을 때 before로 보낼 값. null이면 더 없음(또는 보관 기간 끝)
 */
public record ChatHistoryResponse(List<ChatMessageResponse> messages, String nextCursor) {

    public static ChatHistoryResponse from(ChatHistoryPage page) {
        return new ChatHistoryResponse(page.messages().stream().map(ChatMessageResponse::from).toList(),
                page.nextCursor());
    }

}
