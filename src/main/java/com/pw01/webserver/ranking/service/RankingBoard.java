package com.pw01.webserver.ranking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 보드 하나(대상 × 난이도 × 기간). Redis 키 이름과 만료 시각을 여기서만 만든다(redis-keys.md 랭킹 절 "보드와 순서").
 *
 * @param kind       종류
 * @param targetId   스테이지·지역 키. 진행은 null
 * @param difficulty 난이도. 진행은 null
 * @param period     기간. 진행은 ALL만
 * @param week       주간이면 그 주, 전체면 null
 */
public record RankingBoard(RankingBoardKind kind, String targetId, Integer difficulty, RankingPeriod period,
                           RankingWeek week) {

    private static final String KEY_PREFIX = "pw01:rank:";
    private static final String LOCK_PREFIX = "pw01:rank-lock:";

    public RankingBoard {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(period);
        if ((period == RankingPeriod.WEEKLY) != (week != null)) {
            throw new IllegalArgumentException("주간 보드에만 주가 있습니다: " + period + " " + week);
        }
        if (kind == RankingBoardKind.PROGRESS) {
            if (targetId != null || difficulty != null || period != RankingPeriod.ALL) {
                throw new IllegalArgumentException("진행 보드는 대상·난이도 없이 전체 기간만입니다");
            }
        } else if (kind == RankingBoardKind.TOTAL) {
            if (targetId != null || difficulty == null) {
                throw new IllegalArgumentException("전체 총합 보드는 대상 없이 난이도만 있습니다");
            }
        } else if (targetId == null || difficulty == null) {
            throw new IllegalArgumentException("스테이지·지역 보드는 대상과 난이도가 있어야 합니다");
        }
    }

    public static RankingBoard stage(String stageId, int difficulty, RankingPeriod period, Instant at) {
        return new RankingBoard(RankingBoardKind.STAGE, stageId, difficulty, period, weekOf(period, at));
    }

    public static RankingBoard region(String regionId, int difficulty, RankingPeriod period, Instant at) {
        return new RankingBoard(RankingBoardKind.REGION, regionId, difficulty, period, weekOf(period, at));
    }

    public static RankingBoard total(int difficulty, RankingPeriod period, Instant at) {
        return new RankingBoard(RankingBoardKind.TOTAL, null, difficulty, period, weekOf(period, at));
    }

    public static RankingBoard progress() {
        return new RankingBoard(RankingBoardKind.PROGRESS, null, null, RankingPeriod.ALL, null);
    }

    private static RankingWeek weekOf(RankingPeriod period, Instant at) {
        return period == RankingPeriod.WEEKLY ? RankingWeek.of(at) : null;
    }

    /** 응답 periodKey: "all" 또는 "2026-W42" */
    public String periodKey() {
        return week == null ? RankingPeriod.ALL.value() : week.key();
    }

    /** 키 안의 <보드>: stage:<id>:d<n>:all · stage:<id>:d<n>:week:<YYYY-Www> · region:… · total:d<n>:… · progress:all */
    public String id() {
        String prefix = switch (kind) {
            case PROGRESS -> null;
            case TOTAL -> "total:d" + difficulty;
            case STAGE -> "stage:" + targetId + ":d" + difficulty;
            case REGION -> "region:" + targetId + ":d" + difficulty;
        };
        if (prefix == null) {
            return "progress:all";
        }
        return week == null ? prefix + ":all" : prefix + ":week:" + week.key();
    }

    public String redisKey() {
        return KEY_PREFIX + id();
    }

    public String memberKey() {
        return redisKey() + ":member";
    }

    public String buildKey() {
        return redisKey() + ":build";
    }

    public String buildMemberKey() {
        return buildKey() + ":member";
    }

    public String lockKey() {
        return LOCK_PREFIX + id();
    }

    /** 보드 만료 시각(epoch 초). 전체·진행은 지금 + 전체 보드 수명, 주간은 그 주 끝 + 보관 기간 */
    public long expireAtEpochSeconds(Instant now, Duration allBoardTtl, Duration weeklyRetention) {
        Instant at = week == null ? now.plus(allBoardTtl) : week.end().plus(weeklyRetention);
        return at.getEpochSecond();
    }

}
