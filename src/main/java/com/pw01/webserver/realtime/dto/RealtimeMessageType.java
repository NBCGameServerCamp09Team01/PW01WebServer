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

    /** M23 길드(v1.4): 신청 수락으로 길드원이 됨. data는 {guildId, accountId, nickname, memberCount}. 받는 사람: 길드원 전부(새 사람 포함) */
    GUILD_MEMBER_JOINED,

    /** M23 길드(v1.4): 탈퇴·추방. data는 {guildId, accountId, memberCount, reason(LEFT·KICKED)}. 받는 사람: 남은 길드원 + 나간 사람 */
    GUILD_MEMBER_LEFT,

    /** M23 길드 Lv2(v1.5): 길드원 접속·끊김(상태가 바뀔 때만). data는 {guildId, accountId, online}. 받는 사람: 본인을 뺀 길드원. GuildPresenceNotifier */
    PRESENCE_CHANGED,

    /** M23 길드(v1.4): 해체. data는 {guildId}. 받는 사람: 해체 직전 길드원 + 그 길드 신청자 */
    GUILD_DISBANDED,

    /** M23 길드(v1.4): 가입 신청이 들어옴. data는 {guildId, joinRequestId, accountId, nickname, pendingCount}. 받는 사람: 길드장 */
    GUILD_JOIN_REQUESTED,

    /** M23 길드(v1.4): 길드장이 신청을 거절함. data는 {guildId, guildName, joinRequestId}. 받는 사람: 신청자 */
    GUILD_JOIN_REJECTED

}
