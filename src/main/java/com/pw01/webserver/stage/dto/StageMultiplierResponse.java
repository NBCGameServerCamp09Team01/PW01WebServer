package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.DifficultyMultipliers;

/**
 * ST1 스테이지의 난이도 배율 하나(stage-api.md v1.4 stages[].multipliers). 스테이지 덮어쓰기를 반영한 실제 값이다.
 * 선택 화면 안내 문구용이고, 마리 수는 미리보기 counts가 이미 반영한다.
 */
public record StageMultiplierResponse(int difficulty, double enemyCountMultiplier, double enemyHealthMultiplier,
                                      double enemyDamageMultiplier, double goldAmountMultiplier) {

    public static StageMultiplierResponse from(int difficulty, DifficultyMultipliers multipliers) {
        return new StageMultiplierResponse(difficulty, multipliers.enemyCountMultiplier(),
                multipliers.enemyHealthMultiplier(), multipliers.enemyDamageMultiplier(),
                multipliers.goldAmountMultiplier());
    }

}
