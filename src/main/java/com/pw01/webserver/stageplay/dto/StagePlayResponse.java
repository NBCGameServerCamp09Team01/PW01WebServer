package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stageplay.entity.StagePlay;
import com.pw01.webserver.stageplay.entity.StagePlayEndReason;
import com.pw01.webserver.stageplay.entity.StagePlayStatus;

import java.time.Instant;

/**
 * 스테이지 플레이 하나(P1~P4 응답 data). accountId는 큰 정수라 문자열(README 공통 규칙 "ID").
 * waveCount는 시작 때의 서버 값(결과 검사 기준), endReason·endedAt은 진행 중이면 null.
 */
public record StagePlayResponse(String stagePlayId, String accountId, String stageId, int difficulty, int waveCount,
                                StagePlayStatus status, StagePlayEndReason endReason, Instant startedAt,
                                Instant expiresAt, Instant endedAt) {

    public static StagePlayResponse from(StagePlay play) {
        return new StagePlayResponse(play.getId(), String.valueOf(play.getAccountId()), play.getStageId(),
                play.getDifficulty(), play.getWaveCount(), play.getStatus(), play.getEndReason(),
                play.getStartedAt(), play.getExpiresAt(), play.getEndedAt());
    }

}
