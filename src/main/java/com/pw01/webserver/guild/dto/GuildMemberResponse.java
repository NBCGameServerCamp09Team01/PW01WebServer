package com.pw01.webserver.guild.dto;

import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.entity.GuildRole;

import java.time.Instant;

/**
 * G4 members[] 한 항목, G8 수락 응답의 member. online은 접속 상태 키(pw01:presence)가 있는지(끊긴 뒤 최대 TTL까지 true일 수 있음).
 */
public record GuildMemberResponse(String accountId, String nickname, GuildRole role, Instant joinedAt, boolean online) {

    public static GuildMemberResponse from(AccountGuild member, String nickname, boolean online) {
        return new GuildMemberResponse(member.getAccountId().toString(), nickname, member.getRole(),
                member.getJoinedAt(), online);
    }

}
