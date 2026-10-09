package com.pw01.webserver.stage;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.stage.entity.AccountStageProgress;
import com.pw01.webserver.stage.repository.AccountStageProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 진행 표(account_stage_progress) 마이그레이션과 넣기 쿼리 확인. 제약은 SQL로, 넣기·읽기는 저장소로 본다.
 * 실패하면 V4 마이그레이션의 제약 이름·CHECK 정규식·stage_id 정렬 규칙, 저장소의 insertIfAbsent 쿼리를 먼저 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 계정 아이디·닉네임을 서로 다르게 쓴다.
 */
@IntegrationTest
class StageProgressSchemaTest {

    private static final Instant CLEARED_AT = Instant.parse("2026-10-08T03:00:00.123Z");
    private static final String PLAY_1 = "3f2b8c1e-7a4d-4e2f-9b10-6c5d4e3f2a1b";
    private static final String PLAY_2 = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccountStageProgressRepository repository;

    @Autowired
    TransactionTemplate tx;

    // 확인: 정상 행을 넣고 엔티티로 같은 값을 읽는다(엔티티 칸 = 마이그레이션 칸)
    @Test
    void 넣고_읽기() {
        long accountId = insertAccount("StageSc01", "스테이지스키마01");
        insertRow(accountId, "stage.01.01");

        List<AccountStageProgress> rows = repository.findAllByAccountId(accountId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getStageId()).isEqualTo("stage.01.01");
        assertThat(rows.getFirst().getFirstClearedAt()).isEqualTo(CLEARED_AT);
        assertThat(rows.getFirst().getFirstClearStagePlayId()).isEqualTo(PLAY_1);
        assertThat(repository.existsByAccountIdAndStageId(accountId, "stage.01.01")).isTrue();
        assertThat(repository.existsByAccountIdAndStageId(accountId, "stage.01.02")).isFalse();
    }

    // 확인: 같은 계정·스테이지 두 번은 유일 제약 위반(제약 이름이 오류에 실림)
    @Test
    void 같은_계정_스테이지는_한_행() {
        long accountId = insertAccount("StageSc02", "스테이지스키마02");
        insertRow(accountId, "stage.01.01");

        assertThatThrownBy(() -> insertRow(accountId, "stage.01.01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_account_stage_progress_account_stage");
    }

    // 확인: 다른 계정은 같은 스테이지를 각자 가진다
    @Test
    void 다른_계정은_따로() {
        long first = insertAccount("StageSc03", "스테이지스키마03");
        long second = insertAccount("StageSc04", "스테이지스키마04");
        insertRow(first, "stage.01.01");
        insertRow(second, "stage.01.01");

        assertThat(repository.findAllByAccountId(first)).hasSize(1);
        assertThat(repository.findAllByAccountId(second)).hasSize(1);
    }

    // 확인: 없는 계정은 외래 키 위반
    @Test
    void 없는_계정은_거절() {
        assertThatThrownBy(() -> insertRow(999_999_999L, "stage.01.01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_account_stage_progress_account");
    }

    // 확인: 키 모양 CHECK(대문자·밑줄 금지). 마스터 데이터 검사와 같은 규칙의 마지막 방어.
    // CHECK 위반은 Spring이 무결성 예외로 분류하지 않으므로(UncategorizedSQLException) 부모인 DataAccessException으로 본다
    @Test
    void 키_모양이_틀리면_거절() {
        long accountId = insertAccount("StageSc05", "스테이지스키마05");

        assertThatThrownBy(() -> insertRow(accountId, "Stage.01.01"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_account_stage_progress_stage_id");
        assertThatThrownBy(() -> insertRow(accountId, "stage_01_01"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_account_stage_progress_stage_id");
    }

    // 확인: 플레이 ID CHECK — 스테이지 플레이 ID와 같은 소문자·하이픈 36자 UUID만(대문자, UE FGuid 기본 모양 32자는 거절)
    @Test
    void 판_번호_모양이_틀리면_거절() {
        long accountId = insertAccount("StageSc07", "스테이지스키마07");

        assertThatThrownBy(() -> insertRow(accountId, "stage.01.01", PLAY_1.toUpperCase()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_account_stage_progress_run_id");
        assertThatThrownBy(() -> insertRow(accountId, "stage.01.02", "3F2B8C1E7A4D4E2F9B106C5D4E3F2A1B"))
                .isInstanceOf(DataAccessException.class);
    }

    // 확인: insertIfAbsent는 두 번 불러도 예외 없이 한 행, 시각·플레이 ID는 첫 값 그대로
    @Test
    void 넣기_쿼리는_두_번째를_무시() {
        long accountId = insertAccount("StageSc06", "스테이지스키마06");

        tx.executeWithoutResult(status -> repository.insertIfAbsent(accountId, "stage.01.01", CLEARED_AT, PLAY_1));
        assertThatCode(() -> tx.executeWithoutResult(status -> repository.insertIfAbsent(
                accountId, "stage.01.01", CLEARED_AT.plusSeconds(60), PLAY_2)))
                .doesNotThrowAnyException();

        List<AccountStageProgress> rows = repository.findAllByAccountId(accountId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getFirstClearedAt()).isEqualTo(CLEARED_AT);
        assertThat(rows.getFirst().getFirstClearStagePlayId()).isEqualTo(PLAY_1);
    }

    // 확인: insertIfAbsent도 외래 키 위반은 그대로 예외(INSERT IGNORE처럼 숨기지 않음)
    @Test
    void 넣기_쿼리도_없는_계정은_거절() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> repository.insertIfAbsent(
                999_999_998L, "stage.01.01", CLEARED_AT, PLAY_1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertAccount(String loginId, String nickname) {
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, nickname);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

    private void insertRow(long accountId, String stageId) {
        insertRow(accountId, stageId, PLAY_1);
    }

    private void insertRow(long accountId, String stageId, String stagePlayId) {
        jdbc.update("""
                INSERT INTO account_stage_progress
                    (account_id, stage_id, first_cleared_at, first_clear_run_id, created_at, updated_at)
                VALUES (?, ?, '2026-10-08 03:00:00.123', ?, NOW(3), NOW(3))""", accountId, stageId, stagePlayId);
    }

}
