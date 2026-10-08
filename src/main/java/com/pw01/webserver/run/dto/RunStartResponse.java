package com.pw01.webserver.run.dto;

import com.pw01.webserver.run.entity.Run;

import java.time.Instant;

/** R1 판 시작 응답 data(result-api.md). waveCount는 이 판의 결과 검사 기준(서버 값) */
public record RunStartResponse(String runId, String stageId, int difficulty, int waveCount, Instant issuedAt,
                               Instant expiresAt) {

    public static RunStartResponse from(Run run) {
        return new RunStartResponse(run.getId(), run.getStageId(), run.getDifficulty(), run.getWaveCount(),
                run.getIssuedAt(), run.getExpiresAt());
    }

}
