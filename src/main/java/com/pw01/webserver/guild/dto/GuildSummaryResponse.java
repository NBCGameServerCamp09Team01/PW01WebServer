package com.pw01.webserver.guild.dto;

import com.pw01.webserver.guild.entity.Guild;

import java.time.Instant;

/**
 * 길드 요약(guild-api.md "GuildSummary"). G1 목록 항목, G2·G4의 guild가 같은 필드를 쓴다. ID는 문자열(공통 규칙).
 */
public record GuildSummaryResponse(String guildId, String name, String leaderAccountId, String leaderNickname,
                                   int memberCount, int maxMembers, Instant createdAt) {

    public static GuildSummaryResponse from(Guild guild, String leaderNickname) {
        return new GuildSummaryResponse(guild.getId().toString(), guild.getName(),
                guild.getLeaderAccountId().toString(), leaderNickname,
                guild.getMemberCount(), guild.getMaxMembers(), guild.getCreatedAt());
    }

}
