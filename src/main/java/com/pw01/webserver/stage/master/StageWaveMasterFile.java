package com.pw01.webserver.stage.master;

import java.util.List;
import java.util.Map;

/**
 * master/stage-waves.json의 모양. 스테이지마다 웨이브 구성(stage-api.md "서버 정의 파일").
 * stages.json과는 stageId로 이어진다. 빈 칸(null)은 기본값이거나 몬스터 정의(monsters.json)·난이도(difficulties.json) 값을 쓴다.
 *
 * @param version 파일 버전(1 이상). 값을 바꾸면 1 올린다. 판의 waveVersion "w{이 값}-m{monsters 버전}-d{difficulties 버전}"
 * @param stages  스테이지마다 웨이브
 */
public record StageWaveMasterFile(int version, List<StageWavesEntry> stages) {

    /**
     * @param stageId               스테이지 키(stages.json에 있는 것)
     * @param difficultyMultipliers 이 스테이지에서만 난이도 배율을 덮어씀(M9). 키 = 난이도 번호 문자열(1 이상), 값 = 바꿀 칸만. null이면 덮어쓰기 없음
     * @param waves                 웨이브 목록. 순서가 웨이브 번호(1부터)
     */
    public record StageWavesEntry(String stageId, Map<String, MultiplierOverride> difficultyMultipliers,
                                  List<WaveEntry> waves) {

        /** 덮어쓰기 없는 스테이지(M24 모양) */
        public StageWavesEntry(String stageId, List<WaveEntry> waves) {
            this(stageId, null, waves);
        }

    }

    /**
     * 스테이지의 난이도 배율 덮어쓰기 한 개. null인 칸은 difficulties.json 값을 쓴다(칸 단위).
     * difficulties.json과 같은 칸 이름이다. 다른 칸(name 등)은 파일 읽기에서 실패한다.
     */
    public record MultiplierOverride(Double enemyCountMultiplier, Double enemyHealthMultiplier,
                                     Double enemyDamageMultiplier, Double goldAmountMultiplier) {
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
     * @param monsterId         몬스터 키(monsters.json에 있는 것)
     * @param count             마리 수(1 이상). 난이도 0의 수이자 다른 난이도 계산의 바탕
     * @param countByDifficulty 난이도별 마리 수를 직접 정함(M9). 키 = 난이도 번호 문자열(1 이상), 값 1 이상, 그 난이도부터(계단식). null이면 수 배율로 계산
     * @param spawnGroup        스폰 그룹 이름(위치는 레벨이 정함). null이면 기본 스폰
     * @param boss              null이면 몬스터 정의 값
     * @param goldReward        null이면 몬스터 정의 값
     * @param goldDropChance    null이면 몬스터 정의 값
     * @param affixes           이 줄의 적(마리 수 전부)에 붙는 수식어 키(affix.으로 시작). 모든 난이도에서 같다. null이면 수식어 없음.
     *                          서버는 키 모양만 검사하고 효과는 게임의 수식어 카탈로그가 정한다(새 수식어는 이 파일만 바꿔 넣음)
     */
    public record EnemyEntry(String monsterId, Integer count, Map<String, Integer> countByDifficulty,
                             String spawnGroup, Boolean boss, Integer goldReward, Double goldDropChance,
                             List<String> affixes) {

        /** 수식어가 없는 줄(M9 모양) */
        public EnemyEntry(String monsterId, Integer count, Map<String, Integer> countByDifficulty,
                          String spawnGroup, Boolean boss, Integer goldReward, Double goldDropChance) {
            this(monsterId, count, countByDifficulty, spawnGroup, boss, goldReward, goldDropChance, null);
        }

        /** 난이도별 마리 수가 없는 줄(M24 모양) */
        public EnemyEntry(String monsterId, Integer count, String spawnGroup, Boolean boss, Integer goldReward,
                          Double goldDropChance) {
            this(monsterId, count, null, spawnGroup, boss, goldReward, goldDropChance, null);
        }

    }

}
