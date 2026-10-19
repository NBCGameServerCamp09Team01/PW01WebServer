package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.WaveDef;
import com.pw01.webserver.stage.service.WaveEnemyDef;

import java.util.List;
import java.util.stream.IntStream;

/**
 * ST1 스테이지의 웨이브 미리보기 하나(stage-api.md v1.3·v1.4). 지금 서버 정의 값이다.
 * 골드·확률·쉬는 시간은 싣지 않는다(실제 판의 구성은 스테이지 플레이 응답 waves, 시작 때 고정).
 */
public record StageWavePreviewResponse(int waveNumber, boolean bossWave, List<Enemy> enemies) {

    /**
     * @param wave         난이도 0의 웨이브
     * @param byDifficulty 같은 웨이브의 난이도별 구성(번호 순). 줄(몬스터 종류)은 모든 난이도가 같다
     */
    public static StageWavePreviewResponse from(WaveDef wave, List<WaveDef> byDifficulty) {
        List<Enemy> enemies = IntStream.range(0, wave.enemies().size())
                .mapToObj(j -> Enemy.from(wave.enemies().get(j),
                        byDifficulty.stream().map(difficultyWave -> difficultyWave.enemies().get(j).count()).toList()))
                .toList();
        return new StageWavePreviewResponse(wave.waveNumber(), wave.bossWave(), enemies);
    }

    /**
     * 적 한 줄. 이름은 ST1 monsters에서 찾는다.
     *
     * @param count   난이도 0의 마리 수(= counts[0], v1.3 호환)
     * @param counts  난이도별 마리 수(번호 순, v1.4). 게임은 고른 난이도 칸을 보이고 직접 계산하지 않는다
     * @param affixes 이 줄의 수식어 키(stage-api.md v1.6). 모든 난이도가 같다. 없으면 빈 배열
     */
    public record Enemy(String monsterId, int count, List<Integer> counts, boolean boss, List<String> affixes) {

        public static Enemy from(WaveEnemyDef enemy, List<Integer> counts) {
            return new Enemy(enemy.monsterId(), enemy.count(), counts, enemy.boss(), enemy.affixes());
        }

    }

}
