package com.pw01.webserver.ranking.service;

import com.pw01.webserver.config.RankingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 점수식(ranking-api.md "점수")의 경계를 확인한다. 실패하면 RankingScore의 정수 계산 순서(곱한 뒤 나눔)나 설정 값을 의심한다.
 */
class RankingScoreTest {

    private final RankingScore score = new RankingScore(new RankingProperties(List.of(0), 20, 50, 1000, 9000,
            Duration.ofDays(30), Duration.ofDays(14), Duration.ofSeconds(30), Duration.ofSeconds(60), 500));

    // 확인: 명세 예시(T 300초, t 183.4초 → 4498)
    @Test
    void 명세_예시() {
        assertThat(score.of(300, 183_400, 0)).isEqualTo(4498);
    }

    // 확인: 0초 클리어는 최대(기본 + 시간 점수 최대), 권장 시간에 딱·넘김은 기본 점수만(C-2)
    @Test
    void 경계() {
        assertThat(score.of(300, 0, 0)).isEqualTo(10_000);
        assertThat(score.of(300, 300_000, 0)).isEqualTo(1000);
        assertThat(score.of(300, 410_000, 0)).isEqualTo(1000);
        assertThat(score.of(300, 299_999, 0)).isEqualTo(1000);   // floor(9000 × 1 / 300000) = 0
        assertThat(score.of(300, 299_966, 0)).isEqualTo(1001);   // 남은 34ms → 1점
    }

    // 확인: 빨리 깰수록 점수가 커진다(1초에 30점, T 300초)
    @Test
    void 빠를수록_높다() {
        assertThat(score.of(300, 100_000, 0)).isGreaterThan(score.of(300, 101_000, 0));
        assertThat(score.of(300, 100_000, 0) - score.of(300, 101_000, 0)).isEqualTo(30);
    }

    // 확인: 난이도 배수는 자리만(지금 늘 ×1)
    @Test
    void 난이도_배수_자리() {
        assertThat(RankingScore.difficultyMultiplierPercent(0)).isEqualTo(100);
        assertThat(score.of(300, 183_400, 1)).isEqualTo(4498);
    }

    // 확인: 권장 시간 0·음수 클리어 시간은 마스터 데이터·결과 검사의 버그라 막는다
    @Test
    void 틀린_입력() {
        assertThatThrownBy(() -> score.of(0, 1000, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> score.of(300, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

}
