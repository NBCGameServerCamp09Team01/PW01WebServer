package com.pw01.webserver.ranking.service;

import java.util.Optional;

/**
 * 순위 기간(ranking-api.md "기간"). 쿼리 period 값과 응답 period는 소문자 이름이다.
 */
public enum RankingPeriod {

    ALL("all"),
    WEEKLY("weekly");

    private final String value;

    RankingPeriod(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** 쿼리 값. 없으면 ALL, 모르는 값이면 빈 값(부른 쪽이 VALIDATION_FAILED) */
    public static Optional<RankingPeriod> parse(String raw) {
        if (raw == null) {
            return Optional.of(ALL);
        }
        for (RankingPeriod period : values()) {
            if (period.value.equals(raw)) {
                return Optional.of(period);
            }
        }
        return Optional.empty();
    }

}
