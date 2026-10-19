package com.pw01.webserver.account;

import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V2 마이그레이션(account·account_progress)이 첫 흐름 규칙대로 테이블을 만드는지 SQL로 확인한다(엔티티와 무관).
 * 409를 가를 때 쓰는 유일 제약 이름이 오류에 어떻게 실리는지도 본다(account.uk_…로 옴, CHECK는 ck_…).
 * 실패하면 V2의 칼럼 정렬 규칙(login_id = utf8mb4_0900_bin), 제약 이름, CHECK 정규식을 먼저 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다.
 */
@IntegrationTest
class AccountSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    /** 가입 두 행: 아이디는 입력 그대로 저장되고, 진행 행은 계정 ID로 1:1 연결된다 */
    @Test
    void insertsAccountAndProgress() {
        long accountId = insertAccount("Schema01", "스키마01");
        jdbc.update("""
                INSERT INTO account_progress (account_id, level, experience, total_experience, stat_points, version, created_at, updated_at)
                VALUES (?, 1, 0, 0, 0, 0, NOW(3), NOW(3))""", accountId);

        assertThat(jdbc.queryForObject("SELECT login_id FROM account WHERE account_id = ?", String.class, accountId))
                .isEqualTo("Schema01");
        assertThat(jdbc.queryForObject("SELECT level FROM account_progress WHERE account_id = ?", Integer.class, accountId))
                .isEqualTo(1);
    }

    /** 아이디는 대소문자를 구분한다: 대소문자만 다른 아이디로 따로 가입되고, 조회도 대소문자까지 같은 것만 찾는다 */
    @Test
    void loginIdIsCaseSensitive() {
        long upper = insertAccount("Schema02", "스키마02");
        long lower = insertAccount("schema02", "스키마03");

        assertThat(lower).isNotEqualTo(upper);
        assertThat(jdbc.queryForList("SELECT account_id FROM account WHERE login_id = ?", Long.class, "Schema02"))
                .containsExactly(upper);
        assertThat(jdbc.queryForList("SELECT account_id FROM account WHERE login_id = ?", Long.class, "SCHEMA02"))
                .isEmpty();
    }

    /** 같은 아이디로 또 가입: 유일 제약 위반, 제약 이름은 테이블 이름이 붙은 account.uk_account_login_id */
    @Test
    void sameLoginIdIsDuplicate() {
        insertAccount("Schema04", "스키마04");

        assertThatThrownBy(() -> insertAccount("Schema04", "스키마05"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account.uk_account_login_id");
    }

    /** 닉네임은 대소문자만 다르면(Nick06, NICK06) 같은 닉네임이다 → account.uk_account_nickname */
    @Test
    void nicknameDifferingOnlyInCaseIsDuplicate() {
        insertAccount("Schema06", "Nick06");

        assertThatThrownBy(() -> insertAccount("Schema07", "NICK06"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account.uk_account_nickname");
    }

    /**
     * 형식 규칙 위반은 CHECK가 막는다(요청 검증이 먼저 막아야 하는 마지막 방어). 유일 제약(uk_)이 아니므로 409가 되지 않는다.
     * 아이디 밑줄, 닉네임 앞 공백, 닉네임에 섞인 한글 자모, 목록에 없는 상태를 본다.
     * JdbcTemplate으로 보내면 MySQL CHECK 위반(3819)은 UncategorizedSQLException으로 온다(JPA를 거치면 DataIntegrityViolationException). 둘 다 DataAccessException이다.
     */
    @Test
    void formatViolationsAreRejectedByCheck() {
        assertThatThrownBy(() -> insertAccount("war_08", "스키마08"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_account_login_id");
        assertThatThrownBy(() -> insertAccount("Schema09", " 스키마09"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_account_nickname");
        assertThatThrownBy(() -> insertAccount("Schema10", "스키마ㄱ10"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_account_nickname");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES ('Schema11', 'hash', '스키마11', NULL, 'BANNED', NOW(3), NOW(3))"""))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_account_status");
    }

    /** 진행 행은 있는 계정만 가리킬 수 있다(fk_account_progress_account) */
    @Test
    void progressRequiresAccount() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account_progress (account_id, level, experience, total_experience, stat_points, version, created_at, updated_at)
                VALUES (999999, 1, 0, 0, 0, 0, NOW(3), NOW(3))"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_account_progress_account");
    }

    private long insertAccount(String loginId, String nickname) {
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, nickname);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

}
