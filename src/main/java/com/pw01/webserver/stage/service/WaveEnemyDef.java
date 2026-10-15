package com.pw01.webserver.stage.service;

/**
 * 웨이브의 적 한 줄. 몬스터 정의 값에 웨이브 파일의 덮어쓰기와 난이도를 반영한 값이다.
 * 스테이지 플레이의 wave_plan JSON에도 이 칸 그대로 저장한다. M9 전에 저장한 구성에는 배율 칸이 없으므로
 * 읽을 때 null이면 1.0으로 채운다(stage-play-api.md v1.4).
 *
 * @param monsterId        몬스터 키
 * @param count            마리 수(난이도 반영)
 * @param boss             보스 몬스터
 * @param spawnGroup       스폰 그룹 이름. 없으면 null(기본 스폰)
 * @param goldReward       한 마리 골드(웨이브 배율을 곱하기 전)
 * @param goldDropChance   골드를 떨굴 확률(웨이브 배율을 곱하기 전)
 * @param healthMultiplier 적 체력 배율(난이도). 없으면 1.0
 * @param damageMultiplier 적 공격력 배율(난이도). 없으면 1.0
 */
public record WaveEnemyDef(String monsterId, int count, boolean boss, String spawnGroup, int goldReward,
                           double goldDropChance, Double healthMultiplier, Double damageMultiplier) {

    public WaveEnemyDef {
        healthMultiplier = healthMultiplier == null ? 1.0 : healthMultiplier;
        damageMultiplier = damageMultiplier == null ? 1.0 : damageMultiplier;
    }

    /** 난이도 배율이 없는 줄(난이도 0과 같음) */
    public WaveEnemyDef(String monsterId, int count, boolean boss, String spawnGroup, int goldReward,
                        double goldDropChance) {
        this(monsterId, count, boss, spawnGroup, goldReward, goldDropChance, 1.0, 1.0);
    }

}
