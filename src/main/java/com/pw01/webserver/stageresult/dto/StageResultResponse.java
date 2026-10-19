package com.pw01.webserver.stageresult.dto;

import com.pw01.webserver.stageresult.entity.StageResult;

import java.time.Instant;

/**
 * 결과 하나. 결과 제출의 data.result, 나중에 결과 조회도 같은 모양이다.
 * waveCount는 플레이의 서버 값, submittedAt은 서버가 결과를 저장한 시각.
 */
public record StageResultResponse(String stagePlayId, String stageId, int difficulty, boolean cleared,
                                  int reachedWave, int waveCount, double playTimeSeconds, int earnedGold,
                                  int killCount, Instant submittedAt, Reward reward) {

    /** 서버가 계산한 보상. UE FWarriorStageReward와 칸을 맞춘다 */
    public record Reward(int expGained, int levelBefore, int levelAfter, int statPointsGained) {
    }

    public static StageResultResponse from(StageResult result) {
        return new StageResultResponse(result.getStagePlayId(), result.getStageId(), result.getDifficulty(),
                result.isCleared(), result.getReachedWave(), result.getWaveCount(),
                result.getPlayTimeMs() / 1000.0, result.getEarnedGold(), result.getKillCount(), result.getCreatedAt(),
                new Reward(result.getExpGained(), result.getLevelBefore(), result.getLevelAfter(),
                        result.getStatPointsGained()));
    }

}
