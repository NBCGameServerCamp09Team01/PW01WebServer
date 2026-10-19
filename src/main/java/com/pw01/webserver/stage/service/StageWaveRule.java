package com.pw01.webserver.stage.service;

/**
 * 결과의 도달 웨이브 규칙(계산만). S2 결과 검사가 판에 저장된 waveCount(판 시작 때의 값)로 부른다.
 * 판 시작 뒤 stages.json의 waveCount가 바뀌어도(판 유효 24시간, 재전송) 그 판은 시작 때의 값으로 검사한다.
 * 규칙이 false면 S2가 자기 결과 거절 코드로 거절한다(코드는 result-api.md).
 */
public final class StageWaveRule {

    private StageWaveRule() {
    }

    /**
     * @param waveCount   판 시작 때 저장한 웨이브 수(1 이상)
     * @param cleared     클리어했는가
     * @param reachedWave 마지막으로 시작한 웨이브. 0부터(첫 웨이브 전에 끝나면 0)
     * @return 0 ≤ reachedWave ≤ waveCount 이고, 클리어면 reachedWave == waveCount
     */
    public static boolean isValidReach(int waveCount, boolean cleared, int reachedWave) {
        if (reachedWave < 0 || reachedWave > waveCount) {
            return false;
        }
        return !cleared || reachedWave == waveCount;
    }

}
