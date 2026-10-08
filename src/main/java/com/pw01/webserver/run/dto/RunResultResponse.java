package com.pw01.webserver.run.dto;

import com.pw01.webserver.run.entity.RunResult;

import java.time.Instant;

/**
 * 결과 하나(result-api.md "결과"). R2의 data.result, 나중에 R3의 items[]가 같은 모양이다.
 * waveCount는 판의 서버 값, submittedAt은 서버가 결과를 저장한 시각.
 */
public record RunResultResponse(String runId, String stageId, int difficulty, boolean cleared, int reachedWave,
                                int waveCount, double playTimeSeconds, int earnedGold, int killCount,
                                Instant submittedAt, Reward reward) {

    /** 서버가 계산한 보상. UE FWarriorStageReward와 칸을 맞춘다 */
    public record Reward(int expGained, int levelBefore, int levelAfter, int statPointsGained) {
    }

    public static RunResultResponse from(RunResult result) {
        return new RunResultResponse(result.getRunId(), result.getStageId(), result.getDifficulty(),
                result.isCleared(), result.getReachedWave(), result.getWaveCount(),
                result.getPlayTimeMs() / 1000.0, result.getEarnedGold(), result.getKillCount(), result.getCreatedAt(),
                new Reward(result.getExpGained(), result.getLevelBefore(), result.getLevelAfter(),
                        result.getStatPointsGained()));
    }

}
