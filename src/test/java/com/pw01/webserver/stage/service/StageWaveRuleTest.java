package com.pw01.webserver.stage.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도달 웨이브 규칙 단위 테스트. S2 결과 검사가 판에 저장한 waveCount로 부른다.
 * 실패하면 StageWaveRule의 범위(0 ≤ 도달 ≤ waveCount)와 클리어 조건(도달 == waveCount)을 의심한다.
 */
class StageWaveRuleTest {

    // 확인: 실패한 판은 0부터 waveCount까지 모두 받아들인다(첫 웨이브 전에 끝나면 0)
    @Test
    void 실패_판의_범위() {
        assertThat(StageWaveRule.isValidReach(5, false, 0)).isTrue();
        assertThat(StageWaveRule.isValidReach(5, false, 3)).isTrue();
        assertThat(StageWaveRule.isValidReach(5, false, 5)).isTrue();
        assertThat(StageWaveRule.isValidReach(5, false, -1)).isFalse();
        assertThat(StageWaveRule.isValidReach(5, false, 6)).isFalse();
    }

    // 확인: 클리어한 판은 마지막 웨이브까지 도달해야 한다
    @Test
    void 클리어_판은_마지막_웨이브() {
        assertThat(StageWaveRule.isValidReach(5, true, 5)).isTrue();
        assertThat(StageWaveRule.isValidReach(5, true, 4)).isFalse();
        assertThat(StageWaveRule.isValidReach(5, true, 0)).isFalse();
        assertThat(StageWaveRule.isValidReach(5, true, 6)).isFalse();
    }

}
