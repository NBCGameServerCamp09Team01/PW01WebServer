package com.pw01.webserver.common.chat;

import java.util.List;

/**
 * 채팅 내역 한 묶음.
 *
 * @param messages   새것 → 옛것 순. 게임은 받은 묶음을 그대로 역순으로 출력한다(옛것이 위, 최신이 아래)
 * @param nextCursor 다음(더 옛것) 묶음을 받을 때 before로 그대로 돌려 보내는 값. null이면 더 옛것이 없음(또는 보관 기간 끝)
 */
public record ChatHistoryPage(List<ChatMessage> messages, String nextCursor) {

    public ChatHistoryPage {
        messages = List.copyOf(messages);
    }

    public static ChatHistoryPage empty() {
        return new ChatHistoryPage(List.of(), null);
    }

}
