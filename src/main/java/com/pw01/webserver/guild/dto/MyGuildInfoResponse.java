package com.pw01.webserver.guild.dto;

import com.pw01.webserver.guild.entity.GuildRole;

import java.time.Instant;

/**
 * G4의 guild: 길드 요약 필드 + 내 역할(myRole). JSON에서 한 객체로 나가도록 필드를 펼쳐 둔다.
 */
public record MyGuildInfoResponse(String guildId, String name, String leaderAccountId, String leaderNickname,
                                  int memberCount, int maxMembers, Instant createdAt, GuildRole myRole) {

    public static MyGuildInfoResponse of(GuildSummaryResponse summary, GuildRole myRole) {
        return new MyGuildInfoResponse(summary.guildId(), summary.name(), summary.leaderAccountId(),
                summary.leaderNickname(), summary.memberCount(), summary.maxMembers(), summary.createdAt(), myRole);
    }

}
