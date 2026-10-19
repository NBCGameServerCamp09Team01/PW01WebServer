package com.pw01.webserver.guild.dto;

/**
 * G11 탈퇴 결과.
 *
 * @param guildId   나온 길드
 * @param disbanded 길드장이 혼자라 해체됐으면 true
 */
public record GuildLeaveResponse(String guildId, boolean disbanded) {
}
