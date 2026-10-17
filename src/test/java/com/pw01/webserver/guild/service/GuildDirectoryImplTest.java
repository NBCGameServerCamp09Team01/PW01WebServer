package com.pw01.webserver.guild.service;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.common.chat.GuildDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅이 부르는 계약 GuildDirectory(common/chat)의 길드 구현이 account_guild 테이블과 같은 답을 하는지 본다.
 * 시험용 가짜(InMemoryGuildDirectory)와 같은 약속: 길드 ID는 문자열, 미소속이면 빈 값, 숫자가 아닌 ID는 "그런 길드 없음".
 * 실패하면 GuildDirectoryImpl의 ID 변환(GuildIds), AccountGuildRepository.findAccountIdsByGuildId를 먼저 의심한다.
 */
@IntegrationTest
class GuildDirectoryImplTest {

    @Autowired
    GuildDirectory directory;

    @Autowired
    JdbcTemplate jdbc;

    // 확인: 빈은 길드 구현 하나(채팅이 주입받는 것)
    @Test
    void 빈은_길드_구현() {
        assertThat(directory).isInstanceOf(GuildDirectoryImpl.class);
    }

    // 확인: 길드원은 길드 ID 문자열, 미소속·행 없는 계정은 빈 값
    @Test
    void 소속_길드() {
        long leader = insertAccount(true);
        long loner = insertAccount(true);
        long noRow = insertAccount(false);
        long guildId = insertGuild("디렉토리1", leader);
        join(leader, guildId);

        assertThat(directory.guildIdOf(leader)).contains(String.valueOf(guildId));
        assertThat(directory.guildIdOf(loner)).isEmpty();
        assertThat(directory.guildIdOf(noRow)).isEmpty();
    }

    // 확인: isMember는 그 길드의 길드원일 때만, 숫자가 아닌·없는 길드 ID는 false
    @Test
    void 길드원인지() {
        long leader = insertAccount(true);
        long other = insertAccount(true);
        long guildId = insertGuild("디렉토리2", leader);
        long otherGuild = insertGuild("디렉토리3", other);
        join(leader, guildId);
        join(other, otherGuild);

        assertThat(directory.isMember(String.valueOf(guildId), leader)).isTrue();
        assertThat(directory.isMember(String.valueOf(guildId), other)).isFalse();
        assertThat(directory.isMember("abc", leader)).isFalse();
        assertThat(directory.isMember("0", leader)).isFalse();
        assertThat(directory.isMember("999999999", leader)).isFalse();
    }

    // 확인: memberIds는 그 길드원 전부(계정 ID 순), 숫자가 아닌 ID는 빈 목록
    @Test
    void 길드원_목록() {
        long a = insertAccount(true);
        long b = insertAccount(true);
        long outsider = insertAccount(true);
        long guildId = insertGuild("디렉토리4", a);
        join(a, guildId);
        join(b, guildId);

        assertThat(directory.memberIds(String.valueOf(guildId))).containsExactly(a, b).doesNotContain(outsider);
        assertThat(directory.memberIds("7x")).isEmpty();
    }

    private long insertAccount(boolean withGuildRow) {
        String loginId = "GdD" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, "길디" + loginId.substring(3, 9));
        long accountId = jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
        if (withGuildRow) {
            jdbc.update("""
                    INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                    VALUES (?, NULL, NULL, NULL, 0, NOW(3), NOW(3))""", accountId);
        }
        return accountId;
    }

    private long insertGuild(String name, long leaderAccountId) {
        jdbc.update("""
                INSERT INTO guild (name, leader_account_id, member_count, max_members, version, created_at, updated_at)
                VALUES (?, ?, 1, 30, 0, NOW(3), NOW(3))""", name, leaderAccountId);
        return jdbc.queryForObject("SELECT guild_id FROM guild WHERE name = ?", Long.class, name);
    }

    private void join(long accountId, long guildId) {
        jdbc.update("UPDATE account_guild SET guild_id = ?, role = 'MEMBER', joined_at = NOW(3) WHERE account_id = ?",
                guildId, accountId);
    }

}
