package com.pw01.webserver.ranking.repository;

import com.pw01.webserver.ranking.service.RankingBoard;
import com.pw01.webserver.ranking.service.RankingPeriod;
import com.pw01.webserver.ranking.service.RankingRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 보드를 MySQL에서 읽는다(원천). 쓰는 곳: Redis 보드가 없거나 랭킹 Redis 명령이 실패했을 때의 대신 조회, 다시 채우기, 주간 지역 줄.
 * 보드마다 "한 계정 한 줄"을 내는 원천 쿼리(account_id, score, achieved_at, clear_seq, detail)를 만들고, 그 위에 순서·커서·개수를 씌운다.
 * 순서는 모든 보드가 같다: score DESC → achieved_at → clear_seq(ranking-api.md "보드와 순서", V8 순위 인덱스).
 * <ul>
 *   <li>전체 기간: 최고 기록 표 넷(stage_best_record·region_best_score·total_best_score·account_clear_progress)을 그대로. 순위 인덱스 range로 읽는다</li>
 *   <li>주간: ranking_clear_log에서 그 주만 걸러 계정별 최고(지역·전체 총합은 스테이지별 최고의 합). 창 함수라 주의 기록 수에 비례한다</li>
 * </ul>
 * 시각은 LocalDateTime(UTC)으로 주고받는다(JVM 시간대에 상관없이 DATETIME 값 그대로).
 */
@Repository
@RequiredArgsConstructor
public class RankingBoardQuery {

    private static final String ORDER = " ORDER BY b.score DESC, b.achieved_at, b.clear_seq";

    /** 커서 줄보다 뒤(아래) */
    private static final String AFTER = " WHERE (b.score < :cs OR (b.score = :cs AND (b.achieved_at > :ca"
            + " OR (b.achieved_at = :ca AND b.clear_seq > :cq))))";

    /** 그 줄과 같거나 앞(위): 커서 줄의 순위 = 이 개수 */
    private static final String AT_OR_BEFORE = " WHERE (b.score > :cs OR (b.score = :cs AND (b.achieved_at < :ca"
            + " OR (b.achieved_at = :ca AND b.clear_seq <= :cq))))";

    /** 그 줄보다 앞(위): 내 순위 = 이 개수 + 1 */
    private static final String BEFORE = " WHERE (b.score > :cs OR (b.score = :cs AND (b.achieved_at < :ca"
            + " OR (b.achieved_at = :ca AND b.clear_seq < :cq))))";

    private static final RowMapper<RankingRow> ROW = (rs, n) -> new RankingRow(rs.getLong("account_id"),
            rs.getLong("score"), rs.getObject("achieved_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
            rs.getLong("clear_seq"), rs.getString("detail"));

    private final JdbcClient jdbc;

    /** 커서 다음부터 limit줄(cursor null이면 처음부터) */
    public List<RankingRow> page(RankingBoard board, RankingRow cursor, int limit) {
        Map<String, Object> params = new HashMap<>();
        String sql = "SELECT b.* FROM (" + source(board, null, params) + ") b"
                + (cursor == null ? "" : AFTER) + ORDER + " LIMIT :limit";
        if (cursor != null) {
            putRow(params, cursor);
        }
        params.put("limit", limit);
        return jdbc.sql(sql).params(params).query(ROW).list();
    }

    /** 그 줄의 순위(그 줄과 같거나 앞에 있는 줄 수). 커서 다음 묶음의 첫 순위 = 이 값 + 1 */
    public long countAtOrBefore(RankingBoard board, RankingRow row) {
        return countWhere(board, row, AT_OR_BEFORE);
    }

    /** 그 줄보다 앞에 있는 줄 수. 내 순위 = 이 값 + 1 */
    public long countBefore(RankingBoard board, RankingRow row) {
        return countWhere(board, row, BEFORE);
    }

    /** 보드에 기록이 있는 계정 수 */
    public long count(RankingBoard board) {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("SELECT COUNT(*) FROM (" + source(board, null, params) + ") b")
                .params(params).query(Long.class).single();
    }

    /** 한 계정의 줄 */
    public Optional<RankingRow> findRow(RankingBoard board, long accountId) {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("SELECT b.* FROM (" + source(board, accountId, params) + ") b")
                .params(params).query(ROW).optional();
    }

    /** 다시 채우기: 보드 전체를 batchSize줄씩 넘긴다(순서 없음) */
    public void forEachBatch(RankingBoard board, int batchSize, Consumer<List<RankingRow>> consumer) {
        Map<String, Object> params = new HashMap<>();
        List<RankingRow> batch = new ArrayList<>(batchSize);
        try (var rows = jdbc.sql("SELECT b.* FROM (" + source(board, null, params) + ") b")
                .params(params).query(ROW).stream()) {
            rows.forEach(row -> {
                batch.add(row);
                if (batch.size() >= batchSize) {
                    consumer.accept(List.copyOf(batch));
                    batch.clear();
                }
            });
        }
        if (!batch.isEmpty()) {
            consumer.accept(List.copyOf(batch));
        }
    }

    private long countWhere(RankingBoard board, RankingRow row, String where) {
        Map<String, Object> params = new HashMap<>();
        String sql = "SELECT COUNT(*) FROM (" + source(board, null, params) + ") b" + where;
        putRow(params, row);
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    private static void putRow(Map<String, Object> params, RankingRow row) {
        params.put("cs", row.score());
        params.put("ca", LocalDateTime.ofInstant(row.achievedAt(), ZoneOffset.UTC));
        params.put("cq", row.clearSeq());
    }

    /** 보드의 원천 쿼리: 한 계정 한 줄 (account_id, score, achieved_at, clear_seq, detail). accountId가 있으면 그 계정만 */
    private static String source(RankingBoard board, Long accountId, Map<String, Object> params) {
        if (board.targetId() != null) {
            params.put("target", board.targetId());
        }
        if (board.difficulty() != null) {
            params.put("difficulty", board.difficulty());
        }
        if (accountId != null) {
            params.put("account", accountId);
        }
        String onlyAccount = accountId == null ? "" : " AND account_id = :account";
        if (board.period() == RankingPeriod.WEEKLY) {
            params.put("from", LocalDateTime.ofInstant(board.week().start(), ZoneOffset.UTC));
            params.put("to", LocalDateTime.ofInstant(board.week().end(), ZoneOffset.UTC));
        }

        return switch (board.kind()) {
            case STAGE -> board.period() == RankingPeriod.ALL
                    ? "SELECT account_id, score, achieved_at, clear_seq, CAST(play_time_ms AS CHAR) AS detail"
                    + " FROM stage_best_record WHERE stage_id = :target AND difficulty = :difficulty" + onlyAccount
                    // 그 주 기록 중 계정별 최고(점수 → 먼저 달성 → 먼저 기록)
                    : "SELECT account_id, score, achieved_at, clear_seq, CAST(play_time_ms AS CHAR) AS detail FROM ("
                    + " SELECT account_id, score, achieved_at, clear_seq, play_time_ms,"
                    + " ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY score DESC, achieved_at, clear_seq) AS rn"
                    + " FROM ranking_clear_log WHERE stage_id = :target AND difficulty = :difficulty"
                    + " AND achieved_at >= :from AND achieved_at < :to" + onlyAccount + ") w WHERE w.rn = 1";
            case REGION -> board.period() == RankingPeriod.ALL
                    ? "SELECT account_id, total_score AS score, achieved_at, clear_seq,"
                    + " CAST(cleared_stage_count AS CHAR) AS detail"
                    + " FROM region_best_score WHERE region_id = :target AND difficulty = :difficulty" + onlyAccount
                    : weeklySum("region_id = :target AND ", onlyAccount);
            case TOTAL -> board.period() == RankingPeriod.ALL
                    ? "SELECT account_id, total_score AS score, achieved_at, clear_seq,"
                    + " CAST(cleared_stage_count AS CHAR) AS detail"
                    + " FROM total_best_score WHERE difficulty = :difficulty" + onlyAccount
                    : weeklySum("", onlyAccount);
            case PROGRESS -> "SELECT account_id, cleared_stage_count AS score, achieved_at, clear_seq,"
                    + " furthest_stage_id AS detail FROM account_clear_progress"
                    + (accountId == null ? "" : " WHERE account_id = :account");
        };
    }

    /**
     * 주간 합(지역·전체 총합): 그 주 스테이지별 최고의 합. 달성·순번은 합에 든 기록 중 가장 늦게 기록된 것(= 합이 마지막으로 오른 때).
     * scope는 지역 보드면 "region_id = :target AND ", 전체 총합이면 빈 문자열.
     */
    private static String weeklySum(String scope, String onlyAccount) {
        return "SELECT account_id, CAST(SUM(score) AS SIGNED) AS score, MAX(last_at) AS achieved_at,"
                + " MAX(clear_seq) AS clear_seq, CAST(COUNT(*) AS CHAR) AS detail FROM ("
                + " SELECT account_id, score, clear_seq,"
                + " FIRST_VALUE(achieved_at) OVER (PARTITION BY account_id ORDER BY clear_seq DESC) AS last_at"
                + " FROM (SELECT account_id, score, achieved_at, clear_seq, ROW_NUMBER() OVER"
                + " (PARTITION BY account_id, stage_id ORDER BY score DESC, achieved_at, clear_seq) AS rn"
                + " FROM ranking_clear_log WHERE " + scope + "difficulty = :difficulty"
                + " AND achieved_at >= :from AND achieved_at < :to" + onlyAccount + ") best"
                + " WHERE best.rn = 1) w GROUP BY account_id";
    }

}
