package com.pw01.webserver.guild.dto;

import com.pw01.webserver.guild.entity.GuildJoinRequest;

import java.time.Duration;
import java.time.Instant;

/**
 * 가입 신청 하나(guild-api.md "JoinRequest"). G4 myJoinRequests[], G5 응답, G7 joinRequests[]가 같은 필드를 쓴다.
 * expiresAt은 칼럼이 아니라 신청 시각 + 설정 값(pw01.guild.join-request-ttl)으로 계산한다.
 */
public record GuildJoinRequestResponse(String joinRequestId, String guildId, String guildName, String accountId,
                                       String nickname, Instant requestedAt, Instant expiresAt) {

    public static GuildJoinRequestResponse from(GuildJoinRequest request, String guildName, String nickname,
                                                Duration ttl) {
        return new GuildJoinRequestResponse(request.getId().toString(), request.getGuildId().toString(), guildName,
                request.getAccountId().toString(), nickname, request.requestedAt(), request.expiresAt(ttl));
    }

}
