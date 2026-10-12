package com.pw01.webserver.ranking;

import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실행 계획 비교(학습 기록, 계획서 R-5): ② 최고 기록 표 + 순위 인덱스 vs ① 클리어 기록에서 매번 계정별 최고를 묶기.
 * 평소 빌드에서는 돌지 않는다. 환경 변수 PW01_RANKING_EXPLAIN=true일 때만:
 *   PW01_RANKING_EXPLAIN=true ./gradlew test --tests '*RankingExplainTest'
 * 시험 DB에 계정 1만·클리어 5만을 넣고 EXPLAIN FORMAT=TREE·EXPLAIN ANALYZE를 build/ranking-explain.txt에 남긴다.
 * 쿼리 모양은 RankingBoardQuery가 만드는 것과 같다(원천 쿼리를 파생 표로 감싸고 순서·커서를 씌움).
 */
@IntegrationTest
@EnabledIfEnvironmentVariable(named = "PW01_RANKING_EXPLAIN", matches = "true")
class RankingExplainTest {

    private static final int ACCOUNTS = 10_000;
    private static final int CLEARS = 50_000;
    private static final String DIGITS = "(SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4"
            + " UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
    private static final String NUMBERS = "(SELECT a.d + b.d * 10 + c.d * 100 + e.d * 1000 + f.d * 10000 + 1 AS n FROM "
            + DIGITS + " a CROSS JOIN " + DIGITS + " b CROSS JOIN " + DIGITS + " c CROSS JOIN " + DIGITS + " e CROSS JOIN "
            + DIGITS + " f)";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void 실행_계획을_남긴다() throws IOException {
        RankingFixtures.reset(jdbc, redis);
        seed();

        Map<String, String> queries = new LinkedHashMap<>();
        String bestSource = "SELECT account_id, score, achieved_at, clear_seq, CAST(play_time_ms AS CHAR) AS detail"
                + " FROM stage_best_record WHERE stage_id = 'stage.01.01' AND difficulty = 0";
        queries.put("② 첫 묶음(최고 기록 표)", "SELECT b.* FROM (" + bestSource + ") b"
                + " ORDER BY b.score DESC, b.achieved_at, b.clear_seq LIMIT 21");
        queries.put("② 커서 다음 묶음", "SELECT b.* FROM (" + bestSource + ") b"
                + " WHERE (b.score < 5000 OR (b.score = 5000 AND (b.achieved_at > NOW(3) - INTERVAL 10 DAY"
                + " OR (b.achieved_at = NOW(3) - INTERVAL 10 DAY AND b.clear_seq > 100))))"
                + " ORDER BY b.score DESC, b.achieved_at, b.clear_seq LIMIT 21");
        queries.put("② 내 순위(앞에 있는 줄 수)", "SELECT COUNT(*) FROM (" + bestSource + ") b"
                + " WHERE (b.score > 5000 OR (b.score = 5000 AND (b.achieved_at < NOW(3) - INTERVAL 10 DAY"
                + " OR (b.achieved_at = NOW(3) - INTERVAL 10 DAY AND b.clear_seq < 100))))");
        queries.put("① 첫 묶음(클리어 기록에서 계정별 최고를 매번 묶음)", "SELECT b.* FROM ("
                + "SELECT account_id, score, achieved_at, clear_seq FROM (SELECT account_id, score, achieved_at, clear_seq,"
                + " ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY score DESC, achieved_at, clear_seq) AS rn"
                + " FROM ranking_clear_log WHERE stage_id = 'stage.01.01' AND difficulty = 0) w WHERE w.rn = 1) b"
                + " ORDER BY b.score DESC, b.achieved_at, b.clear_seq LIMIT 21");
        queries.put("주간 첫 묶음(이번 주, 클리어 기록)", "SELECT b.* FROM ("
                + "SELECT account_id, score, achieved_at, clear_seq FROM (SELECT account_id, score, achieved_at, clear_seq,"
                + " ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY score DESC, achieved_at, clear_seq) AS rn"
                + " FROM ranking_clear_log WHERE stage_id = 'stage.01.01' AND difficulty = 0"
                + " AND achieved_at >= NOW(3) - INTERVAL 7 DAY AND achieved_at < NOW(3)) w WHERE w.rn = 1) b"
                + " ORDER BY b.score DESC, b.achieved_at, b.clear_seq LIMIT 21");

        StringBuilder out = new StringBuilder();
        out.append("# 랭킹 실행 계획 (MySQL ").append(jdbc.queryForObject("SELECT VERSION()", String.class))
                .append(", 계정 ").append(ACCOUNTS).append(", 클리어 ").append(CLEARS)
                .append(", stage_best_record ").append(count("stage_best_record")).append("줄)\n\n");
        for (Map.Entry<String, String> query : queries.entrySet()) {
            out.append("## ").append(query.getKey()).append("\n\n```sql\n").append(query.getValue()).append("\n```\n\n");
            out.append("EXPLAIN FORMAT=TREE\n```\n").append(explain("EXPLAIN FORMAT=TREE " + query.getValue())).append("\n```\n\n");
            out.append("EXPLAIN ANALYZE\n```\n").append(explain("EXPLAIN ANALYZE " + query.getValue())).append("\n```\n\n");
        }
        Path file = Path.of("build", "ranking-explain.txt");
        Files.writeString(file, out, StandardCharsets.UTF_8);
        System.out.println(out);
        assertThat(file).exists();
    }

    private void seed() {
        jdbc.update("INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)"
                + " SELECT CONCAT('exp', LPAD(n, 5, '0')), 'x', CONCAT('설명', LPAD(n, 5, '0')), NULL, 'ACTIVE', NOW(3), NOW(3)"
                + " FROM " + NUMBERS + " t WHERE n <= " + ACCOUNTS);
        long firstId = jdbc.queryForObject("SELECT MIN(account_id) FROM account WHERE login_id LIKE 'exp%'", Long.class);
        jdbc.update("INSERT INTO ranking_clear_log (stage_play_id, account_id, stage_id, region_id, difficulty, score,"
                + " play_time_ms, recommended_time_ms, achieved_at, created_at, updated_at)"
                + " SELECT UUID(), " + firstId + " + MOD(n, " + ACCOUNTS + "), IF(MOD(n, 2) = 0, 'stage.01.01', 'stage.01.02'),"
                + " 'region.01', 0, 1000 + FLOOR(9000 * GREATEST(0, 300000 - (60000 + MOD(n * 7919, 240000))) / 300000),"
                + " 60000 + MOD(n * 7919, 240000), 300000, NOW(3) - INTERVAL MOD(n * 13, 1728000) SECOND, NOW(3), NOW(3)"
                + " FROM " + NUMBERS + " t WHERE n <= " + CLEARS);
        jdbc.update("INSERT INTO stage_best_record (stage_id, difficulty, account_id, score, play_time_ms, achieved_at,"
                + " clear_seq, stage_play_id, clear_count, created_at, updated_at)"
                + " SELECT stage_id, difficulty, account_id, score, play_time_ms, achieved_at, clear_seq, stage_play_id, cnt,"
                + " NOW(3), NOW(3) FROM (SELECT l.*, ROW_NUMBER() OVER (PARTITION BY account_id, stage_id, difficulty"
                + " ORDER BY score DESC, achieved_at, clear_seq) AS rn,"
                + " COUNT(*) OVER (PARTITION BY account_id, stage_id, difficulty) AS cnt FROM ranking_clear_log l) t WHERE rn = 1");
        jdbc.execute("ANALYZE TABLE ranking_clear_log, stage_best_record");
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private String explain(String sql) {
        List<String> lines = jdbc.queryForList(sql, String.class);
        return String.join("\n", lines);
    }

}
