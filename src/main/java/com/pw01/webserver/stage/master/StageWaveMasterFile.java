package com.pw01.webserver.stage.master;

import java.util.List;

/**
 * master/stage-waves.json의 모양. 스테이지마다 웨이브 구성(stage-api.md "서버 정의 파일").
 * stages.json과는 stageId로 이어진다. 빈 칸(null)은 기본값이거나 몬스터 정의(monsters.json) 값을 쓴다.
 *
 * @param version 파일 버전(1 이상). 값을 바꾸면 1 올린다. 판의 waveVersion "w{이 값}-m{monsters 버전}"
 * @param stages  스테이지마다 웨이브
 */
public record StageWaveMasterFile(int version, List<StageWavesEntry> stages) {

    /**
     * @param stageId 스테이지 키(stages.json에 있는 것)
     * @param waves   웨이브 목록. 순서가 웨이브 번호(1부터)
     */
    public record StageWavesEntry(String stageId, List<WaveEntry> waves) {
    }

    /**
     * @param restSeconds          이 웨이브 앞 쉬는 시간(초). null이면 0(게임 기본)
     * @param bossWave             보스 웨이브. null이면 false
     * @param goldChanceMultiplier 골드 확률 배율. null이면 1.0
     * @param goldAmountMultiplier 골드 양 배율. null이면 1.0
     * @param enemies              나올 적. 1줄 이상
     */
    public record WaveEntry(Double restSeconds, Boolean bossWave, Double goldChanceMultiplier,
                            Double goldAmountMultiplier, List<EnemyEntry> enemies) {
    }

    /**
     * @param monsterId      몬스터 키(monsters.json에 있는 것)
     * @param count          마리 수(1 이상)
     * @param spawnGroup     스폰 그룹 이름(위치는 레벨이 정함). null이면 기본 스폰
     * @param boss           null이면 몬스터 정의 값
     * @param goldReward     null이면 몬스터 정의 값
     * @param goldDropChance null이면 몬스터 정의 값
     */
    public record EnemyEntry(String monsterId, Integer count, String spawnGroup, Boolean boss, Integer goldReward,
                             Double goldDropChance) {
    }

}
