package com.pw01.webserver.stageresult.repository;

import com.pw01.webserver.stageresult.service.ResultTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 결과 표(stage_play_result)를 계정별로 모은 값. 누적 통계 대조(StatTotalReconciler)의 원천이다.
 * 계정별로 읽으므로 인덱스 ix_stage_play_result_account_created(account_id, …)를 쓴다(V5).
 */
@Repository
@RequiredArgsConstructor
public class StageResultTotalsQuery {

    private final JdbcTemplate jdbc;

    /** 계정 ID → 모은 값. 결과가 없는 계정은 빠진다 */
    public Map<Long, ResultTotals> byAccount(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        String in = accountIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, ResultTotals> map = new HashMap<>();
        jdbc.query("""
                SELECT account_id, COUNT(*), SUM(cleared), SUM(kill_count), SUM(earned_gold), SUM(play_time_ms),
                       MAX(reached_wave), MIN(CASE WHEN cleared THEN play_time_ms END)
                FROM stage_play_result
                WHERE account_id IN (""" + in + ") GROUP BY account_id",
                rs -> {
                    long bestClear = rs.getLong(8);
                    Long bestClearMs = rs.wasNull() ? null : bestClear;
                    map.put(rs.getLong(1), new ResultTotals(rs.getLong(2), rs.getLong(3), rs.getLong(4),
                            rs.getLong(5), rs.getLong(6), rs.getLong(7), bestClearMs));
                }, accountIds.toArray());
        return map;
    }

}
