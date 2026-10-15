package com.pw01.webserver.realtime.dto;

/**
 * 서버 → 게임 메시지 종류. 루트 docs/contracts/realtime-api.md "메시지 종류" 표와 같다.
 * 새 종류는 그 표에 먼저 적고(명세 v1.x) 여기에 더한다. 게임은 모르는 종류를 무시한다.
 * 이름 규칙: &lt;영역&gt;_&lt;무엇 또는 일어난 일&gt;(영역 SESSION·ACCOUNT·CHAT·GUILD·PRESENCE, 일어난 일은 과거형).
 */
public enum RealtimeMessageType {

    /** 같은 계정이 다른 곳에서 로그인했다. 보낸 뒤 종료 코드 4001로 닫는다. data는 {} */
    SESSION_REPLACED,

    /** 예약: 계정 진행이 바뀌었다. v1에서는 보내지 않는다(보내는 경우가 생기면 명세 v1.1에 적고 쓴다) */
    ACCOUNT_CHANGED,

    /**
     * 초안(M23 채팅): 전체·길드 채팅 한 줄. data는 common.chat.ChatMessage.toRealtimeData().
     * 전체는 RealtimeNotifier.sendToAll, 길드는 sendToGuild(그 길드원에게만)
     */
    CHAT_MESSAGE,

    /** 초안(M23 소셜): 길드 가입. data는 {guildId, accountId, nickname, memberCount}. sendToGuild */
    GUILD_MEMBER_JOINED,

    /** 초안(M23 소셜): 길드 탈퇴. data는 {guildId, accountId, memberCount, reason}. 나간 사람도 목록에 넣어 sendToGuild */
    GUILD_MEMBER_LEFT,

    /** 초안·선택(M23 소셜, 현황에 접속 표시를 넣을 때): 길드원 접속·끊김. data는 {guildId, accountId, online}. sendToGuild */
    PRESENCE_CHANGED,

    /** 초안·선택(M23 소셜, 해체를 넣을 때): 길드 해체. data는 {guildId}. sendToGuild */
    GUILD_DISBANDED

}
