package com.pw01.webserver.account.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 계정 누적 통계(account_stat_total, 지시서 1-5c). 키마다 합계 한 줄이고 결과마다 더한다.
 * 값을 더하기만 하므로 엔티티 없이 "없으면 넣고 있으면 더하는" 쿼리 하나로 쓴다(같은 계정의 동시 결과에도 합이 맞음).
 */
@Repository
@RequiredArgsConstructor
public class AccountStatTotalRepository {

    private static final String ADD_SQL = """
            INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE total = total + VALUES(total), updated_at = VALUES(updated_at)""";

    private final JdbcTemplate jdbc;

    /** 키마다 amount를 더한다(0은 건너뜀). 호출하는 쪽 트랜잭션에서 실행된다 */
    public void add(Long accountId, Map<String, Long> amounts) {
        Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        amounts.forEach((key, amount) -> {
            if (amount != 0) {
                jdbc.update(ADD_SQL, accountId, key, amount, now, now);
            }
        });
    }

    /** 키 하나의 합계. 없으면 0 */
    public long total(Long accountId, String statKey) {
        Long value = jdbc.query("SELECT total FROM account_stat_total WHERE account_id = ? AND stat_key = ?",
                rs -> rs.next() ? rs.getLong(1) : null, accountId, statKey);
        return value == null ? 0 : value;
    }

}
