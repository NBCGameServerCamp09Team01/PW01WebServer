package com.pw01.webserver.stage.master;

import java.util.List;

/**
 * master/difficulties.json의 모양. 난이도 목록과 기본 배율(stage-api.md "서버 정의 파일"·"난이도").
 * 난이도 0은 "조절 없음"이라 배율이 모두 1.0이어야 한다(DifficultyMasterValidator).
 * 빠진 칸을 알아보려고 칸을 상자 타입으로 받는다.
 *
 * @param version      파일 버전(1 이상). 값을 바꾸면 1 올린다. 판의 waveVersion "w…-m…-d{이 값}"
 * @param difficulties 난이도 목록. 번호 0부터 빈틈없이
 */
public record DifficultyMasterFile(int version, List<DifficultyEntry> difficulties) {

    /**
     * @param difficulty             난이도 번호. 스테이지 플레이·결과·랭킹의 difficulty와 같은 값
     * @param name                   화면 이름
     * @param enemyCountMultiplier   기본 마리 수 배율(> 0). 적 줄에 countByDifficulty가 없을 때만 쓴다
     * @param enemyHealthMultiplier  기본 적 체력 배율(> 0)
     * @param enemyDamageMultiplier  기본 적 공격력 배율(> 0)
     * @param goldAmountMultiplier   기본 골드 양 배율(> 0). 웨이브 골드 양 배율에 곱한다
     * @param scoreMultiplierPercent 랭킹 점수 배수(%, 1 이상). 모든 스테이지 공통
     */
    public record DifficultyEntry(Integer difficulty, String name, Double enemyCountMultiplier,
                                  Double enemyHealthMultiplier, Double enemyDamageMultiplier,
                                  Double goldAmountMultiplier, Integer scoreMultiplierPercent) {
    }

}
