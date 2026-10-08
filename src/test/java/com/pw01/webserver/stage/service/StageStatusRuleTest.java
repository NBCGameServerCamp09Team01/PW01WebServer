package com.pw01.webserver.stage.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 해금 규칙 단위 테스트(DB 없음). 첫 스테이지는 진행 행 없이 열리고, 선행을 클리어하면 다음이 열린다.
 * 실패하면 StageStatusRule의 순서(클리어 먼저 → 열림 → 잠김)를 의심한다.
 */
class StageStatusRuleTest {

    private static final StageDef FIRST = new StageDef("stage.01.01", "region.01", "스테이지 1", 1, null, 5);
    private static final StageDef SECOND = new StageDef("stage.01.02", "region.01", "스테이지 2", 2, "stage.01.01", 5);

    // 확인: 새 계정(클리어 없음) — 첫 스테이지 OPEN, 둘째 LOCKED
    @Test
    void 새_계정() {
        assertThat(StageStatusRule.of(FIRST, Set.of())).isEqualTo(StageStatus.OPEN);
        assertThat(StageStatusRule.of(SECOND, Set.of())).isEqualTo(StageStatus.LOCKED);
    }

    // 확인: 첫 스테이지 클리어 — 첫째 CLEARED, 둘째 OPEN
    @Test
    void 선행을_클리어하면_다음이_열림() {
        Set<String> cleared = Set.of("stage.01.01");

        assertThat(StageStatusRule.of(FIRST, cleared)).isEqualTo(StageStatus.CLEARED);
        assertThat(StageStatusRule.of(SECOND, cleared)).isEqualTo(StageStatus.OPEN);
    }

    // 확인: 클리어한 스테이지도 열린 것(다시 할 수 있음)
    @Test
    void 클리어한_스테이지도_열림() {
        Set<String> cleared = Set.of("stage.01.01", "stage.01.02");

        assertThat(StageStatusRule.isUnlocked(SECOND, cleared)).isTrue();
        assertThat(StageStatusRule.of(SECOND, cleared)).isEqualTo(StageStatus.CLEARED);
    }

}
