package com.pw01.webserver.stageplay.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * C1 체크포인트 저장 요청(루트 docs/contracts/stage-play-api.md "C1. 저장").
 * state는 게임이 정하는 JSON 객체다. 서버는 객체인지와 크기만 확인하고(PlayCheckpointService) 안의 칸은 읽지 않는다.
 * clearedWave의 위쪽 범위(플레이의 waveCount 미만)는 플레이를 읽은 뒤 서비스에서 확인한다.
 * requestId는 받지 않는다(덮어쓰기라 같은 요청이 다시 와도 결과가 같다).
 */
public record PlayCheckpointSaveRequest(

        @NotNull(message = "contentVersion이 필요합니다.")
        @Min(value = 1, message = "contentVersion은 1 이상이어야 합니다.")
        Integer contentVersion,

        @NotNull(message = "clearedWave가 필요합니다.")
        @Min(value = 1, message = "clearedWave는 1 이상이어야 합니다.")
        Integer clearedWave,

        @NotNull(message = "elapsedSeconds가 필요합니다.")
        @PositiveOrZero(message = "elapsedSeconds는 0 이상이어야 합니다.")
        Double elapsedSeconds,

        @NotNull(message = "state가 필요합니다.")
        JsonNode state
) {
}
