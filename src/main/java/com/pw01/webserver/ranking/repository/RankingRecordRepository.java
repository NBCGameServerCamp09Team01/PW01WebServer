package com.pw01.webserver.ranking.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 랭킹 표(V7) 쓰기. 결과 트랜잭션 안에서만 불린다(RankingRecordService, MANDATORY).
 * JPA 엔티티 없이 SQL로 쓴다: 결과 저장 쪽 영속성 컨텍스트(계정·플레이·결과 엔티티)를 건드리지 않고,
 * "점수가 오를 때만 덮어씀"을 한 문장(INSERT … ON DUPLICATE KEY UPDATE)으로 해서 동시 저장에도 어긋나지 않게 한다.
 * JdbcClient는 트랜잭션에 묶인 같은 커넥션을 쓴다(JpaTransactionManager가 JDBC 커넥션을 내준다).
 * 시각은 LocalDateTime(UTC)으로 넘긴다(JVM 시간대에 상관없이 DATETIME 값 그대로).
 */
@Repository
@RequiredArgsConstructor
public class RankingRecordRepository {

    private final JdbcClient jdbc;

    /** 클리어 기록 한 줄(덧붙이기만). 돌려주는 값이 기록 순번(clear_seq) */
    public long insertClear(String stagePlayId, long accountId, String stageId, String regionId, int difficulty,
                            int score, long playTimeMs, long recommendedTimeMs, Instant achievedAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO ranking_clear_log
                            (stage_play_id, account_id, stage_id, region_id, difficulty, score, play_time_ms,
                             recommended_time_ms, achieved_at, created_at, updated_at)
                        VALUES (:stagePlayId, :accountId, :stageId, :regionId, :difficulty, :score, :playTimeMs,
                                :recommendedTimeMs, :at, :at, :at)""")
                .param("stagePlayId", stagePlayId)
                .param("accountId", accountId)
                .param("stageId", stageId)
                .param("regionId", regionId)
                .param("difficulty", difficulty)
                .param("score", score)
                .param("playTimeMs", playTimeMs)
                .param("recommendedTimeMs", recommendedTimeMs)
                .param("at", utc(achievedAt))
                .update(keys, "clear_seq");
        return Objects.requireNonNull(keys.getKey(), "clear_seq를 받지 못했습니다").longValue();
    }

    /** 이 계정이 이 스테이지를 (어느 난이도든) 클리어 기록한 적이 있나. 진행(처음 클리어) 판단 */
    public boolean hasClear(long accountId, String stageId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM ranking_clear_log WHERE account_id = :accountId"
                        + " AND stage_id = :stageId)")
                .param("accountId", accountId)
                .param("stageId", stageId)
                .query(Long.class).single() != 0;
    }

    /**
     * 스테이지 최고 기록: 없으면 새 줄, 있으면 새 점수가 **클 때만** 그 기록으로 통째로 덮어쓴다(같은 점수의 늦은 기록은 그대로).
     * 클리어 횟수는 늘 +1. MySQL은 SET을 왼쪽부터 적용하고 뒤 식이 바뀐 값을 보므로 score는 맨 끝에 바꾼다.
     */
    public void upsertStageBest(String stageId, int difficulty, long accountId, int score, long playTimeMs,
                                Instant achievedAt, long clearSeq, String stagePlayId) {
        jdbc.sql("""
                        INSERT INTO stage_best_record
                            (stage_id, difficulty, account_id, score, play_time_ms, achieved_at, clear_seq, stage_play_id,
                             clear_count, created_at, updated_at)
                        VALUES (:stageId, :difficulty, :accountId, :score, :playTimeMs, :at, :clearSeq, :stagePlayId,
                                1, :at, :at) AS new
                        ON DUPLICATE KEY UPDATE
                            play_time_ms  = IF(new.score > stage_best_record.score, new.play_time_ms, stage_best_record.play_time_ms),
                            achieved_at   = IF(new.score > stage_best_record.score, new.achieved_at, stage_best_record.achieved_at),
                            clear_seq     = IF(new.score > stage_best_record.score, new.clear_seq, stage_best_record.clear_seq),
                            stage_play_id = IF(new.score > stage_best_record.score, new.stage_play_id, stage_best_record.stage_play_id),
                            clear_count   = stage_best_record.clear_count + 1,
                            updated_at    = new.updated_at,
                            score         = GREATEST(new.score, stage_best_record.score)""")
                .param("stageId", stageId)
                .param("difficulty", difficulty)
                .param("accountId", accountId)
                .param("score", score)
                .param("playTimeMs", playTimeMs)
                .param("at", utc(achievedAt))
                .param("clearSeq", clearSeq)
                .param("stagePlayId", stagePlayId)
                .update();
    }

    /** 스테이지 최고 기록의 지금 순번. 방금 넣은 순번과 같으면 이번 기록이 최고가 된 것 */
    public long stageBestClearSeq(String stageId, int difficulty, long accountId) {
        return jdbc.sql("SELECT clear_seq FROM stage_best_record WHERE stage_id = :stageId AND difficulty = :difficulty"
                        + " AND account_id = :accountId")
                .param("stageId", stageId)
                .param("difficulty", difficulty)
                .param("accountId", accountId)
                .query(Long.class).single();
    }

    /** 지역 합: 그 스테이지들의 최고 점수 합과 클리어한 스테이지 수(같은 계정·난이도) */
    public RegionSum sumRegion(long accountId, int difficulty, Collection<String> stageIds) {
        return jdbc.sql("SELECT CAST(COALESCE(SUM(score), 0) AS SIGNED) AS total, COUNT(*) AS cnt FROM stage_best_record"
                        + " WHERE account_id = :accountId AND difficulty = :difficulty AND stage_id IN (:stageIds)")
                .param("accountId", accountId)
                .param("difficulty", difficulty)
                .param("stageIds", stageIds)
                .query((rs, n) -> new RegionSum(rs.getLong("total"), rs.getInt("cnt")))
                .single();
    }

    /** 지역 점수 줄을 통째로 덮어쓴다(합은 내려가지 않으므로 비교 없이) */
    public void upsertRegion(String regionId, int difficulty, long accountId, long totalScore, int clearedStageCount,
                             Instant achievedAt, long clearSeq) {
        jdbc.sql("""
                        INSERT INTO region_best_score
                            (region_id, difficulty, account_id, total_score, cleared_stage_count, achieved_at, clear_seq,
                             created_at, updated_at)
                        VALUES (:regionId, :difficulty, :accountId, :total, :cleared, :at, :clearSeq, :at, :at) AS new
                        ON DUPLICATE KEY UPDATE
                            total_score = new.total_score,
                            cleared_stage_count = new.cleared_stage_count,
                            achieved_at = new.achieved_at,
                            clear_seq = new.clear_seq,
                            updated_at = new.updated_at""")
                .param("regionId", regionId)
                .param("difficulty", difficulty)
                .param("accountId", accountId)
                .param("total", totalScore)
                .param("cleared", clearedStageCount)
                .param("at", utc(achievedAt))
                .param("clearSeq", clearSeq)
                .update();
    }

    /** 전체 총합: 모든 스테이지 최고 점수의 합과 클리어한 스테이지 수(같은 계정·난이도) */
    public RegionSum sumAll(long accountId, int difficulty) {
        return jdbc.sql("SELECT CAST(COALESCE(SUM(score), 0) AS SIGNED) AS total, COUNT(*) AS cnt FROM stage_best_record"
                        + " WHERE account_id = :accountId AND difficulty = :difficulty")
                .param("accountId", accountId)
                .param("difficulty", difficulty)
                .query((rs, n) -> new RegionSum(rs.getLong("total"), rs.getInt("cnt")))
                .single();
    }

    /** 전체 총합 줄을 통째로 덮어쓴다(합은 내려가지 않으므로 비교 없이) */
    public void upsertTotal(int difficulty, long accountId, long totalScore, int clearedStageCount, Instant achievedAt,
                            long clearSeq) {
        jdbc.sql("""
                        INSERT INTO total_best_score
                            (difficulty, account_id, total_score, cleared_stage_count, achieved_at, clear_seq,
                             created_at, updated_at)
                        VALUES (:difficulty, :accountId, :total, :cleared, :at, :clearSeq, :at, :at) AS new
                        ON DUPLICATE KEY UPDATE
                            total_score = new.total_score,
                            cleared_stage_count = new.cleared_stage_count,
                            achieved_at = new.achieved_at,
                            clear_seq = new.clear_seq,
                            updated_at = new.updated_at""")
                .param("difficulty", difficulty)
                .param("accountId", accountId)
                .param("total", totalScore)
                .param("cleared", clearedStageCount)
                .param("at", utc(achievedAt))
                .param("clearSeq", clearSeq)
                .update();
    }

    /** 이 계정이 클리어 기록한 스테이지 키(난이도 상관없이, 겹침 없이) */
    public List<String> clearedStageIds(long accountId) {
        return jdbc.sql("SELECT DISTINCT stage_id FROM ranking_clear_log WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query(String.class).list();
    }

    /** 진행 줄을 통째로 덮어쓴다(수는 내려가지 않으므로 비교 없이) */
    public void upsertProgress(long accountId, int clearedStageCount, String furthestStageId, Instant achievedAt,
                               long clearSeq) {
        jdbc.sql("""
                        INSERT INTO account_clear_progress
                            (account_id, cleared_stage_count, furthest_stage_id, achieved_at, clear_seq, created_at, updated_at)
                        VALUES (:accountId, :cleared, :furthest, :at, :clearSeq, :at, :at) AS new
                        ON DUPLICATE KEY UPDATE
                            cleared_stage_count = new.cleared_stage_count,
                            furthest_stage_id = new.furthest_stage_id,
                            achieved_at = new.achieved_at,
                            clear_seq = new.clear_seq,
                            updated_at = new.updated_at""")
                .param("accountId", accountId)
                .param("cleared", clearedStageCount)
                .param("furthest", furthestStageId)
                .param("at", utc(achievedAt))
                .param("clearSeq", clearSeq)
                .update();
    }

    private static LocalDateTime utc(Instant at) {
        return LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    /** 지역 합 */
    public record RegionSum(long total, int clearedStageCount) {
    }

}
