package com.pw01.webserver.stage.service;

import com.pw01.webserver.stage.master.StageWaveMasterFile.MultiplierOverride;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 난이도 계산 규칙(stage-api.md "난이도") 단위 테스트. 파일 없이 값만 넣는다.
 * 실패하면 StageDifficultyRule의 반올림(0.5 올림·최소 1)·보스 줄·계단식(d 이하에서 가장 큰 키)·칸 단위 덮어쓰기를 의심한다.
 */
class StageDifficultyRuleTest {

    private static final DifficultyMultipliers HARD = new DifficultyMultipliers(1.5, 1.5, 1.3, 2.0);

    // 확인: 덮어쓰기가 없으면 기본 배율, 있으면 적힌 칸만 바뀐다(칸 단위)
    @Test
    void 배율_덮어쓰기는_칸_단위() {
        assertThat(StageDifficultyRule.multipliers(HARD, null)).isEqualTo(HARD);
        assertThat(StageDifficultyRule.multipliers(HARD, new MultiplierOverride(null, 1.2, 1.2, null)))
                .isEqualTo(new DifficultyMultipliers(1.5, 1.2, 1.2, 2.0));
        assertThat(StageDifficultyRule.multipliers(HARD, new MultiplierOverride(null, null, null, null)))
                .isEqualTo(HARD);
    }

    // 확인: 배율 줄은 round(count × 배율), 0.5는 올림, 최소 1. 보스 줄은 그대로
    @Test
    void 배율_줄() {
        assertThat(StageDifficultyRule.count(2, false, null, 2, 1.5)).isEqualTo(3);
        assertThat(StageDifficultyRule.count(3, false, null, 2, 1.5)).isEqualTo(5);   // 4.5 → 5
        assertThat(StageDifficultyRule.count(2, false, null, 2, 1.2)).isEqualTo(2);   // 2.4 → 2
        assertThat(StageDifficultyRule.count(1, false, null, 2, 0.4)).isEqualTo(1);   // 0.4 → 0 → 최소 1
        assertThat(StageDifficultyRule.count(1, true, null, 2, 3.0)).isEqualTo(1);    // 보스는 그대로
        assertThat(StageDifficultyRule.count(4, false, Map.of(), 0, 1.0)).isEqualTo(4);
    }

    // 확인: 직접 수 줄은 d 이하에서 가장 큰 키의 값(없으면 count), 배율을 곱하지 않는다. 더한 난이도는 아래 값을 이어받는다
    @Test
    void 직접_수_줄은_계단식() {
        Map<String, Integer> byDifficulty = Map.of("1", 4, "2", 5);
        assertThat(StageDifficultyRule.count(3, false, byDifficulty, 0, 9.0)).isEqualTo(3);
        assertThat(StageDifficultyRule.count(3, false, byDifficulty, 1, 9.0)).isEqualTo(4);
        assertThat(StageDifficultyRule.count(3, false, byDifficulty, 2, 9.0)).isEqualTo(5);
        assertThat(StageDifficultyRule.count(3, false, byDifficulty, 3, 9.0)).isEqualTo(5);

        Map<String, Integer> hardOnly = Map.of("2", 3);
        assertThat(StageDifficultyRule.count(2, false, hardOnly, 1, 9.0)).isEqualTo(2);
        assertThat(StageDifficultyRule.count(2, false, hardOnly, 2, 9.0)).isEqualTo(3);
        // 보스 줄도 직접 정하면 그 값
        assertThat(StageDifficultyRule.count(1, true, Map.of("2", 2), 2, 1.0)).isEqualTo(2);
    }

}
