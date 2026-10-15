package com.pw01.webserver.stage.service;

/**
 * 한 난이도의 배율 네 개(stage-api.md "난이도"). difficulties.json 값에 스테이지 덮어쓰기를 반영한 값이다.
 *
 * @param enemyCountMultiplier  마리 수 배율(countByDifficulty가 없는 줄에만)
 * @param enemyHealthMultiplier 적 체력 배율. 게임이 최대·현재 체력에 곱함
 * @param enemyDamageMultiplier 적 공격력 배율. 게임이 공격력에 곱함
 * @param goldAmountMultiplier  골드 양 배율. 웨이브 골드 양 배율에 곱함
 */
public record DifficultyMultipliers(double enemyCountMultiplier, double enemyHealthMultiplier,
                                    double enemyDamageMultiplier, double goldAmountMultiplier) {

    /** 난이도 0(조절 없음) */
    public static final DifficultyMultipliers NONE = new DifficultyMultipliers(1.0, 1.0, 1.0, 1.0);

}
