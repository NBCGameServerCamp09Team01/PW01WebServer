package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.StageDef;

/**
 * ST1 스테이지 정의 하나(stage-api.md). 모두에게 같다. requires는 없으면 null로 나간다(칸은 늘 있음).
 * recommendedClearSeconds는 랭킹 점수의 기준 시간(v1.2)이다.
 */
public record StageResponse(String stageId, String name, int order, String requires, int waveCount,
                            int recommendedClearSeconds) {

    public static StageResponse from(StageDef stage) {
        return new StageResponse(stage.stageId(), stage.name(), stage.order(), stage.requires(), stage.waveCount(),
                stage.recommendedClearSeconds());
    }

}
