package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 랭킹 설정 값(pw01.ranking.*). 기본값은 application.properties에 있다.
 * 값의 뜻은 루트 docs/contracts/ranking-api.md("점수"·"쿼리")와 redis-keys.md 랭킹 절 "설정 값"이 기준이다.
 *
 * @param difficulties    받는 난이도 목록(지금 0만). 밖의 값은 400 VALIDATION_FAILED
 * @param pageSize        목록 한 묶음 기본 줄 수
 * @param maxPageSize     목록 한 묶음 최대 줄 수
 * @param scoreBase       클리어 기본 점수
 * @param scoreTimeWeight 남은 시간 점수의 최대(남은 시간 / 권장 시간 × 이 값)
 * @param allBoardTtl     전체·진행 보드 Redis 수명. 고치거나 다시 채울 때마다 다시 건다
 * @param weeklyRetention 주간 보드를 그 주가 끝난 뒤 남겨 두는 시간
 * @param rebuildLockTtl  다시 채우기 잠금 시간(pw01:rank-lock)
 * @param buildTtl        임시 보드 수명. 잠금 시간보다 길어야 한다
 * @param buildBatchSize  다시 채울 때 Redis에 한 번에 넣는 줄 수
 */
@ConfigurationProperties("pw01.ranking")
public record RankingProperties(List<Integer> difficulties, int pageSize, int maxPageSize, int scoreBase,
                                int scoreTimeWeight, Duration allBoardTtl, Duration weeklyRetention,
                                Duration rebuildLockTtl, Duration buildTtl, int buildBatchSize) {

    public RankingProperties {
        if (difficulties == null || difficulties.isEmpty() || difficulties.stream().anyMatch(d -> d == null || d < 0)) {
            throw new IllegalArgumentException("pw01.ranking.difficulties는 0 이상 정수가 하나 이상이어야 합니다: " + difficulties);
        }
        difficulties = List.copyOf(difficulties);
        if (pageSize < 1 || maxPageSize < pageSize) {
            throw new IllegalArgumentException("pw01.ranking.page-size는 1 이상, max-page-size 이하여야 합니다: "
                    + pageSize + " / " + maxPageSize);
        }
        if (scoreBase < 0 || scoreTimeWeight < 0) {
            throw new IllegalArgumentException("pw01.ranking.score-base·score-time-weight는 0 이상이어야 합니다");
        }
        requireWholeSeconds("pw01.ranking.all-board-ttl", allBoardTtl);
        requireWholeSeconds("pw01.ranking.weekly-retention", weeklyRetention);
        requireWholeSeconds("pw01.ranking.rebuild-lock-ttl", rebuildLockTtl);
        requireWholeSeconds("pw01.ranking.build-ttl", buildTtl);
        if (buildTtl.compareTo(rebuildLockTtl) <= 0) {
            // 임시 보드가 잠금보다 먼저 사라지면, 잠금을 쥔 서버가 빈 보드를 바꿔 끼울 수 있다(redis-keys.md "값의 관계")
            throw new IllegalArgumentException("pw01.ranking.build-ttl은 rebuild-lock-ttl보다 길어야 합니다: "
                    + buildTtl + " / " + rebuildLockTtl);
        }
        if (buildBatchSize < 1) {
            throw new IllegalArgumentException("pw01.ranking.build-batch-size는 1 이상이어야 합니다: " + buildBatchSize);
        }
    }

    /** Redis EXPIRE가 초 단위라 1초 이상, 초 아래 값이 없어야 한다(redis-keys.md "설정 값") */
    private static void requireWholeSeconds(String name, Duration value) {
        if (value == null || value.compareTo(Duration.ofSeconds(1)) < 0 || value.toMillisPart() != 0) {
            throw new IllegalArgumentException(name + "은 1초 이상의 초 단위여야 합니다: " + value);
        }
    }

}
