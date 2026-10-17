package com.pw01.webserver.guild;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * 길드 테스트용 SQL 도우미. 계정·길드·소속·신청 행을 직접 만든다(쓰기 API를 거치지 않고 상태를 준비할 때).
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임·길드 이름이 겹치지 않게 임의 값을 붙인다.
 */
public final class GuildTestAccounts {

    private GuildTestAccounts() {
    }

    /** 계정 하나(account_guild 행 없이 — 길드 기능이 그 자리에서 만드는 길도 같이 시험된다) */
    public static long account(JdbcTemplate jdbc) {
        String loginId = "GdT" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, "길테" + loginId.substring(3, 9));
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

    /** 길드 만들기 조건(스테이지 1 클리어)을 채운 계정 */
    public static long clearedAccount(JdbcTemplate jdbc) {
        long accountId = account(jdbc);
        jdbc.update("""
                INSERT INTO account_stage_progress (account_id, stage_id, first_cleared_at, created_at, updated_at)
                VALUES (?, 'stage.01.01', NOW(3), NOW(3), NOW(3))""", accountId);
        return accountId;
    }

    /** 길드 이름 뒤에 붙일 임의 영문·숫자(이름 규칙 안, 12자를 넘지 않게 4자) */
    public static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 4);
    }

    /** 서버 길드 수를 0으로(상한 5가 테스트 사이에 쌓이지 않게). 다른 테스트가 SQL로 넣은 guild 행은 세지 않는다 */
    public static void resetGuildCount(JdbcTemplate jdbc) {
        jdbc.update("UPDATE guild_counter SET guild_count = 0 WHERE counter_id = 1");
    }

    /** guild 행 + 길드장의 account_guild 행(정원·인원을 정해서) */
    public static long guildWithLeader(JdbcTemplate jdbc, String name, long leaderId, int memberCount, int maxMembers) {
        jdbc.update("""
                INSERT INTO guild (name, leader_account_id, member_count, max_members, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0, NOW(3), NOW(3))""", name, leaderId, memberCount, maxMembers);
        long guildId = jdbc.queryForObject("SELECT guild_id FROM guild WHERE name = ?", Long.class, name);
        jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, ?, 'LEADER', NOW(3), 0, NOW(3), NOW(3))
                ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id), role = 'LEADER', joined_at = NOW(3)""",
                leaderId, guildId);
        return guildId;
    }

}
