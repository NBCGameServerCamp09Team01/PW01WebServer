package com.pw01.webserver.guild.dto;

/** G8 수락 결과: 새 길드원과 반영 뒤 길드원 수 */
public record GuildApproveResponse(GuildMemberResponse member, int memberCount) {
}
