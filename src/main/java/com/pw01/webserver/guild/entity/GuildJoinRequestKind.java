package com.pw01.webserver.guild.entity;

/**
 * 대기 중 관계의 종류. 지금은 신청(사람 → 길드)만 있다.
 * 초대(길드장 → 사람)를 넣을 때 INVITATION을 더하고, V10의 CHECK(ck_guild_join_request_kind)를 넓히는 마이그레이션을 같이 낸다.
 */
public enum GuildJoinRequestKind {
    APPLICATION
}
