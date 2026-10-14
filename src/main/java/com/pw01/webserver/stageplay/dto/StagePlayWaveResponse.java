package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stage.service.WaveDef;
import com.pw01.webserver.stage.service.WaveEnemyDef;

import java.util.List;

/**
 * 스테이지 플레이의 웨이브 하나(stage-play-api.md v1.3 waves[]). 시작 때 고정한 값이고, 몬스터 정의 값을 풀어 둔 것이다.
 * 게임은 이 값으로만 웨이브를 만든다(몬스터 키 → UE 몬스터 카탈로그 → BP).
 */
public record StagePlayWaveResponse(int waveNumber, double restSeconds, boolean bossWave, double goldChanceMultiplier,
                                    double goldAmountMultiplier, List<Enemy> enemies) {

    public static StagePlayWaveResponse from(WaveDef wave) {
        return new StagePlayWaveResponse(wave.waveNumber(), wave.restSeconds(), wave.bossWave(),
                wave.goldChanceMultiplier(), wave.goldAmountMultiplier(),
                wave.enemies().stream().map(Enemy::from).toList());
    }

    /** 적 한 줄. spawnGroup은 없으면 null(칸은 늘 있음) */
    public record Enemy(String monsterId, int count, boolean boss, String spawnGroup, int goldReward,
                        double goldDropChance) {

        public static Enemy from(WaveEnemyDef enemy) {
            return new Enemy(enemy.monsterId(), enemy.count(), enemy.boss(), enemy.spawnGroup(), enemy.goldReward(),
                    enemy.goldDropChance());
        }

    }

}
