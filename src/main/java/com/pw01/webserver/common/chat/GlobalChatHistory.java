package com.pw01.webserver.common.chat;

/**
 * 전체 채팅 저장 자리(M23 뼈대 약속). 채팅 모듈이 전체 채팅을 보낼 때마다 append를 거쳐 메시지를 만든다.
 * 지금은 NoStoreGlobalChatHistory(저장 안 함)가 끼워져 있다. 나중에 저장하게 되면 이 인터페이스의 저장 구현을 @Primary 빈으로 더하고
 * 불러오기 경로(예약 GET /chats/global)를 연다 — 보내는 코드와 CHAT_MESSAGE 모양은 그대로다.
 */
public interface GlobalChatHistory {

    /** 전체 채팅 한 줄을 만든다(저장하는 구현이면 저장도). 번호와 시각을 매겨 돌려준다 */
    ChatMessage append(Long senderAccountId, String senderNickname, String text);

    /** 최신부터 size개를 새것 → 옛것 순으로. 저장하지 않는 구현은 빈 묶음 */
    ChatHistoryPage loadLatest(String before, int size);

    /** 저장하는 구현인지. false면 불러오기 경로를 열지 않는다 */
    boolean stores();

}
