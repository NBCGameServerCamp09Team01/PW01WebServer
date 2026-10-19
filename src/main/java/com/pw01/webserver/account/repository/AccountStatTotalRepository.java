package com.pw01.webserver.account.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 계정 누적 통계(account_stat_total, 지시서 1-5c). 키마다 값 한 줄이고 결과마다 바꾼다.
 * 엔티티 없이 "없으면 넣고 있으면 바꾸는" 쿼리 하나로 쓴다(같은 계정의 동시 결과에도 값이 맞음).
 * 키마다 바꾸는 방법이 다르다: 합계는 더하기(add), 최고 기록은 최댓값(max)·최솟값(min).
 * 원천은 결과 표(stage_play_result)이고 이 표는 그 요약이다. 어긋나면 대조(StatTotalReconciler)가 결과 표 값으로 고친다.
 */
@Repository
@RequiredArgsConstructor
public class AccountStatTotalRepository {

    private static final String ADD_SQL = """
            INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE total = total + VALUES(total), updated_at = VALUES(updated_at)""";
    private static final String MAX_SQL = """
            INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE total = GREATEST(total, VALUES(total)), updated_at = VALUES(updated_at)""";
    private static final String MIN_SQL = """
            INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE total = LEAST(total, VALUES(total)), updated_at = VALUES(updated_at)""";

    private final JdbcTemplate jdbc;

    /** 키마다 amount를 더한다(0은 건너뜀). 호출하는 쪽 트랜잭션에서 실행된다 */
    public void add(Long accountId, Map<String, Long> amounts) {
        Timestamp now = now();
        amounts.forEach((key, amount) -> {
            if (amount != 0) {
                jdbc.update(ADD_SQL, accountId, key, amount, now, now);
            }
        });
    }

    /** 지금 값과 value 중 큰 값으로(없으면 value). 호출하는 쪽 트랜잭션에서 실행된다 */
    public void max(Long accountId, String statKey, long value) {
        Timestamp now = now();
        jdbc.update(MAX_SQL, accountId, statKey, value, now, now);
    }

    /** 지금 값과 value 중 작은 값으로(없으면 value). 호출하는 쪽 트랜잭션에서 실행된다 */
    public void min(Long accountId, String statKey, long value) {
        Timestamp now = now();
        jdbc.update(MIN_SQL, accountId, statKey, value, now, now);
    }

    /** 키 하나의 합계. 없으면 0 */
    public long total(Long accountId, String statKey) {
        Long value = jdbc.query("SELECT total FROM account_stat_total WHERE account_id = ? AND stat_key = ?",
                rs -> rs.next() ? rs.getLong(1) : null, accountId, statKey);
        return value == null ? 0 : value;
    }

    /** 계정의 모든 키 → 값. 줄이 없는 키는 빠진다(최고 기록이 아직 없는 경우 등) */
    public Map<String, Long> totals(Long accountId) {
        Map<String, Long> map = new HashMap<>();
        jdbc.query("SELECT stat_key, total FROM account_stat_total WHERE account_id = ?",
                rs -> {
                    map.put(rs.getString(1), rs.getLong(2));
                }, accountId);
        return map;
    }

    /** 여러 계정의 모든 키(대조용). 계정 ID → (키 → 값). 줄이 없는 계정은 빠진다 */
    public Map<Long, Map<String, Long>> totals(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        String in = accountIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, Map<String, Long>> map = new HashMap<>();
        jdbc.query("SELECT account_id, stat_key, total FROM account_stat_total WHERE account_id IN (" + in + ")",
                rs -> {
                    map.computeIfAbsent(rs.getLong(1), id -> new HashMap<>()).put(rs.getString(2), rs.getLong(3));
                }, accountIds.toArray());
        return map;
    }

    /**
     * 대조용 덮어쓰기: 지금 값이 expected일 때만 바꾼다(읽은 뒤 다른 결과가 바꿨으면 건드리지 않음 → 0을 돌려줌).
     * expected가 null이면 "줄이 없을 때만 넣기", value가 null이면 "줄 지우기"다. 바꾼 줄 수를 돌려준다.
     */
    public int replaceIf(Long accountId, String statKey, Long expected, Long value) {
        Timestamp now = now();
        if (expected == null) {
            return value == null ? 0 : jdbc.update("""
                    INSERT IGNORE INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)""", accountId, statKey, value, now, now);
        }
        if (value == null) {
            return jdbc.update("DELETE FROM account_stat_total WHERE account_id = ? AND stat_key = ? AND total = ?",
                    accountId, statKey, expected);
        }
        return jdbc.update("""
                UPDATE account_stat_total SET total = ?, updated_at = ?
                WHERE account_id = ? AND stat_key = ? AND total = ?""", value, now, accountId, statKey, expected);
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

}
