package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stage.service.WaveDef;
import com.pw01.webserver.stageplay.entity.StagePlay;
import com.pw01.webserver.stageplay.entity.StagePlayEndReason;
import com.pw01.webserver.stageplay.entity.StagePlayStatus;

import java.time.Instant;
import java.util.List;

/**
 * 스테이지 플레이 하나(P1~P4 응답 data). accountId는 큰 정수라 문자열(README 공통 규칙 "ID").
 * waveCount는 시작 때의 서버 값(결과 검사 기준), endReason·endedAt은 진행 중이면 null.
 * waveVersion·waves는 시작 때 고정한 웨이브 구성(v1.3). 저장 전에 시작한 플레이는 StagePlayService가 지금 정의로 채우거나 null로 넘긴다.
 */
public record StagePlayResponse(String stagePlayId, String accountId, String stageId, int difficulty, int waveCount,
                                String waveVersion, List<StagePlayWaveResponse> waves,
                                StagePlayStatus status, StagePlayEndReason endReason, Instant startedAt,
                                Instant expiresAt, Instant endedAt) {

    /**
     * @param waveVersion 이 플레이의 웨이브 정보 버전. 구성이 없으면 null
     * @param waves       이 플레이의 웨이브 구성. 없으면 null(칸은 null로 나감)
     */
    public static StagePlayResponse from(StagePlay play, String waveVersion, List<WaveDef> waves) {
        return new StagePlayResponse(play.getId(), String.valueOf(play.getAccountId()), play.getStageId(),
                play.getDifficulty(), play.getWaveCount(), waveVersion,
                waves == null ? null : waves.stream().map(StagePlayWaveResponse::from).toList(),
                play.getStatus(), play.getEndReason(), play.getStartedAt(), play.getExpiresAt(), play.getEndedAt());
    }

}
