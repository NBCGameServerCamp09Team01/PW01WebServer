package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.WaveDef;
import com.pw01.webserver.stage.service.WaveEnemyDef;

import java.util.List;

/**
 * ST1 스테이지의 웨이브 미리보기 하나(stage-api.md v1.3). 지금 서버 정의 값이다.
 * 골드·확률·쉬는 시간은 싣지 않는다(실제 판의 구성은 스테이지 플레이 응답 waves, 시작 때 고정).
 */
public record StageWavePreviewResponse(int waveNumber, boolean bossWave, List<Enemy> enemies) {

    public static StageWavePreviewResponse from(WaveDef wave) {
        return new StageWavePreviewResponse(wave.waveNumber(), wave.bossWave(),
                wave.enemies().stream().map(Enemy::from).toList());
    }

    /** 적 한 줄. 이름은 ST1 monsters에서 찾는다 */
    public record Enemy(String monsterId, int count, boolean boss) {

        public static Enemy from(WaveEnemyDef enemy) {
            return new Enemy(enemy.monsterId(), enemy.count(), enemy.boss());
        }

    }

}
