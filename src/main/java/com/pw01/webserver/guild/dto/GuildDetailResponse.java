package com.pw01.webserver.guild.dto;

/**
 * G2 길드 하나(가입 전 정보). 길드원 목록은 길드원에게만 보이므로(G4) 여기에 없다.
 */
public record GuildDetailResponse(GuildSummaryResponse guild) {
}
