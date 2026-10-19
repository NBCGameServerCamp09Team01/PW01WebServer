package com.pw01.webserver.common.chat;

/**
 * 길드 채팅 저장·불러오기. 채팅 모듈이 구현한다(M23 두 모듈 약속, 10/16 — 전체·길드 채팅은 둘 다 채팅 모듈). 저장 방식(표·캐시)은 구현이 정한다.
 * 규칙(10/16 결정, 값은 설정 값, 확정은 채팅 명세): 보관 기간 3일, 한 번에 기본 10개(size 최대 50), 최신부터 커서로.
 * 해체된 길드의 줄은 소셜이 내는 GuildDisbandedEvent를 받아 채팅이 지운다.
 */
public interface GuildChatHistory {

    /**
     * 길드 채팅 한 줄을 저장한다. 저장 번호(messageId)와 시각을 매겨 돌려준다. 부르는 쪽 트랜잭션 안에서 부른다.
     */
    ChatMessage append(String guildId, Long senderAccountId, String senderNickname, String text);

    /**
     * 최신부터 size개를 새것 → 옛것 순으로. before가 null이면 가장 최신부터, 있으면 그 커서보다 옛것부터(그 줄은 빼고).
     * 보관 기간 밖은 돌려주지 않는다.
     */
    ChatHistoryPage loadLatest(String guildId, String before, int size);

}
