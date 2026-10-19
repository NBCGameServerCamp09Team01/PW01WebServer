package com.pw01.webserver.guild;

import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V10 마이그레이션(guild·account_guild·guild_join_request·guild_counter)의 제약을 SQL로 확인한다(엔티티와 무관).
 * 이 제약들은 서비스 판정 뒤의 마지막 방어다(정원·한 사람 하나·이름 유일·같은 길드 신청 하나).
 * 실패하면 V10의 제약 이름·CHECK 식·name 정렬 규칙(utf8mb4_0900_bin)을 먼저 의심한다.
 * MySQL CHECK 위반(3819)은 JdbcTemplate에서 UncategorizedSQLException으로 오므로 DataAccessException으로 본다(AccountSchemaTest와 같음).
 * 테스트끼리 DB를 함께 쓰므로 계정 아이디·닉네임·길드 이름을 서로 다르게 쓴다.
 */
@IntegrationTest
class GuildSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    // 확인: guild_counter 행 하나(counter_id = 1)가 V10에서 만들어지고, 두 번째 행은 CHECK가 막는다
    @Test
    void 길드_수_행은_하나() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild_counter WHERE counter_id = 1", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO guild_counter (counter_id, guild_count, version, created_at, updated_at)
                VALUES (2, 0, 0, NOW(3), NOW(3))"""))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_guild_counter_single");
    }

    // 확인: 이름은 대소문자를 가린다 — 대소문자만 다르면 다른 길드, 완전히 같으면 uk_guild_name
    @Test
    void 이름은_대소문자를_가려_유일() {
        long leader = insertAccount("GuildSc01", "길드스키마01");
        insertGuild("Dawn", leader, 1, 30);
        insertGuild("dawn", leader, 1, 30);

        assertThat(jdbc.queryForList("SELECT name FROM guild WHERE name = 'Dawn'", String.class)).containsExactly("Dawn");
        assertThatThrownBy(() -> insertGuild("Dawn", leader, 1, 30))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("guild.uk_guild_name");
    }

    // 확인: 이름 형식(2~12자, 앞뒤·연속 공백 금지, 자모·기호 금지)은 CHECK ck_guild_name이 막는다. 가운데 공백 하나는 됨
    @Test
    void 이름_형식은_CHECK() {
        long leader = insertAccount("GuildSc02", "길드스키마02");
        assertThatCode(() -> insertGuild("새벽 기사단", leader, 1, 30)).doesNotThrowAnyException();

        for (String bad : new String[]{"새", " 새벽", "새벽 ", "새벽  기사", "새벽ㄱ", "Dawn_1"}) {
            assertThatThrownBy(() -> insertGuild(bad, leader, 1, 30))
                    .as(bad).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_guild_name");
        }
    }

    // 확인: 인원은 1 이상 정원 이하(ck_guild_member_count) — 정원 넘침의 마지막 방어
    @Test
    void 인원은_1부터_정원까지() {
        long leader = insertAccount("GuildSc03", "길드스키마03");
        assertThatThrownBy(() -> insertGuild("인원영", leader, 0, 30))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_guild_member_count");
        assertThatThrownBy(() -> insertGuild("인원넘침", leader, 31, 30))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_guild_member_count");

        long guildId = insertGuild("인원가득", leader, 30, 30);
        assertThatThrownBy(() -> jdbc.update("UPDATE guild SET member_count = member_count + 1 WHERE guild_id = ?", guildId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_guild_member_count");
    }

    // 확인: account_guild 행은 무소속(셋 다 NULL) 또는 소속(셋 다 값)만 된다, 역할은 LEADER·MEMBER만
    @Test
    void 소속_세_칼럼은_같이() {
        long leader = insertAccount("GuildSc04", "길드스키마04");
        long other = insertAccount("GuildSc05", "길드스키마05");
        long another = insertAccount("GuildSc06", "길드스키마06");
        long guildId = insertGuild("세칸", leader, 1, 30);

        assertThatCode(() -> insertPerson(leader, guildId, "LEADER")).doesNotThrowAnyException();
        assertThatCode(() -> insertPerson(other, null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, ?, NULL, NOW(3), 0, NOW(3), NOW(3))""", another, guildId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_account_guild_membership");
        assertThatThrownBy(() -> insertPerson(another, guildId, "OWNER"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_account_guild_role");
    }

    // 확인: account_guild 행은 계정마다 하나(기본 키) — 한 사람 길드 하나의 마지막 방어
    @Test
    void 소속_행은_계정마다_하나() {
        long account = insertAccount("GuildSc07", "길드스키마07");
        insertPerson(account, null, null);

        assertThatThrownBy(() -> insertPerson(account, null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("PRIMARY");
    }

    // 확인: 없는 길드는 가리킬 수 없고(fk_account_guild_guild), 길드원이 남은 길드는 지울 수 없다(해체는 소속을 먼저 비움)
    @Test
    void 길드_외래_키() {
        long leader = insertAccount("GuildSc08", "길드스키마08");
        long other = insertAccount("GuildSc09", "길드스키마09");
        long guildId = insertGuild("외래키", leader, 1, 30);
        insertPerson(leader, guildId, "LEADER");

        assertThatThrownBy(() -> insertPerson(other, 999999L, "MEMBER"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_account_guild_guild");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM guild WHERE guild_id = ?", guildId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_account_guild_guild");
    }

    // 확인: 같은 사람·같은 길드 신청은 하나(uk_guild_join_request_account_guild), 종류는 APPLICATION만, 없는 길드에는 신청 불가
    @Test
    void 신청_제약() {
        long leader = insertAccount("GuildSc10", "길드스키마10");
        long applicant = insertAccount("GuildSc11", "길드스키마11");
        long guildId = insertGuild("신청제약", leader, 1, 30);
        insertRequest(guildId, applicant, "APPLICATION");

        assertThatThrownBy(() -> insertRequest(guildId, applicant, "APPLICATION"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("guild_join_request.uk_guild_join_request_account_guild");
        assertThatThrownBy(() -> insertRequest(guildId, leader, "INVITATION"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_guild_join_request_kind");
        assertThatThrownBy(() -> insertRequest(999999L, applicant, "APPLICATION"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_guild_join_request_guild");
    }

    private long insertAccount(String loginId, String nickname) {
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, nickname);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

    private long insertGuild(String name, long leaderAccountId, int memberCount, int maxMembers) {
        jdbc.update("""
                INSERT INTO guild (name, leader_account_id, member_count, max_members, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0, NOW(3), NOW(3))""", name, leaderAccountId, memberCount, maxMembers);
        return jdbc.queryForObject("SELECT guild_id FROM guild WHERE name = ?", Long.class, name);
    }

    private void insertPerson(long accountId, Long guildId, String role) {
        jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, ?, ?, IF(? IS NULL, NULL, NOW(3)), 0, NOW(3), NOW(3))""", accountId, guildId, role, guildId);
    }

    private void insertRequest(long guildId, long accountId, String kind) {
        jdbc.update("""
                INSERT INTO guild_join_request (guild_id, account_id, kind, created_at, updated_at)
                VALUES (?, ?, ?, NOW(3), NOW(3))""", guildId, accountId, kind);
    }

}
