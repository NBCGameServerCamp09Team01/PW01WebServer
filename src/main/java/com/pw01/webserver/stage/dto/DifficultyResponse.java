package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.DifficultyDef;

/** ST1 난이도 하나(stage-api.md v1.4). 선택 화면 버튼용. 배율은 스테이지마다 다를 수 있어 StageResponse.multipliers에 있다 */
public record DifficultyResponse(int difficulty, String name) {

    public static DifficultyResponse from(DifficultyDef difficulty) {
        return new DifficultyResponse(difficulty.difficulty(), difficulty.name());
    }

}
