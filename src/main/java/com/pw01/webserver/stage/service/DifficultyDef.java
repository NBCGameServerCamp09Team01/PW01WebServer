package com.pw01.webserver.stage.service;

/**
 * 난이도 하나(difficulties.json). 번호는 스테이지 플레이·결과·랭킹의 difficulty와 같다.
 *
 * @param difficulty             난이도 번호. 0 = 조절 없음
 * @param name                   화면 이름
 * @param multipliers            기본 배율(스테이지가 덮어쓰지 않을 때)
 * @param scoreMultiplierPercent 랭킹 점수 배수(%). 모든 스테이지 공통
 */
public record DifficultyDef(int difficulty, String name, DifficultyMultipliers multipliers,
                            int scoreMultiplierPercent) {
}
