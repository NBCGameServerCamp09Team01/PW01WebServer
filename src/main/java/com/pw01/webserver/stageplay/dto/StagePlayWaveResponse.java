package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stage.service.WaveDef;
import com.pw01.webserver.stage.service.WaveEnemyDef;

import java.util.List;

/**
 * 스테이지 플레이의 웨이브 하나(stage-play-api.md v1.3·v1.4 waves[]). 시작 때 고정한 값이고, 몬스터 정의 값과 난이도를 반영한 것이다.
 * 게임은 이 값으로만 웨이브를 만든다(몬스터 키 → UE 몬스터 카탈로그 → BP).
 */
public record StagePlayWaveResponse(int waveNumber, double restSeconds, boolean bossWave, double goldChanceMultiplier,
                                    double goldAmountMultiplier, List<Enemy> enemies) {

    public static StagePlayWaveResponse from(WaveDef wave) {
        return new StagePlayWaveResponse(wave.waveNumber(), wave.restSeconds(), wave.bossWave(),
                wave.goldChanceMultiplier(), wave.goldAmountMultiplier(),
                wave.enemies().stream().map(Enemy::from).toList());
    }

    /**
     * 적 한 줄. spawnGroup은 없으면 null(칸은 늘 있음).
     * healthMultiplier·damageMultiplier는 난이도 배율(v1.4). 게임이 그 적의 체력(최대·현재)·공격력에 곱한다.
     * affixes는 이 줄의 적 모두에 붙는 수식어 키(v1.5). 없으면 빈 배열(칸은 늘 있음). 효과는 게임의 수식어 카탈로그가 정한다
     */
    public record Enemy(String monsterId, int count, boolean boss, String spawnGroup, int goldReward,
                        double goldDropChance, double healthMultiplier, double damageMultiplier,
                        List<String> affixes) {

        public static Enemy from(WaveEnemyDef enemy) {
            return new Enemy(enemy.monsterId(), enemy.count(), enemy.boss(), enemy.spawnGroup(), enemy.goldReward(),
                    enemy.goldDropChance(), enemy.healthMultiplier(), enemy.damageMultiplier(), enemy.affixes());
        }

    }

}
