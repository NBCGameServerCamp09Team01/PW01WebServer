package com.pw01.webserver.ranking.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * 주간 보드의 주(ISO 주, 월요일 00:00 UTC 시작). 키는 "2026-W42" 모양(주 기준 연도 + 두 자리 주 번호).
 * 연말·연초는 달력 연도와 주 기준 연도가 다를 수 있다(예: 2026-12-31은 2026-W53, 2027-01-01도 같은 주).
 *
 * @param monday 그 주 월요일(UTC 날짜)
 */
public record RankingWeek(LocalDate monday) {

    public RankingWeek {
        if (monday == null || monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("월요일이 아닙니다: " + monday);
        }
    }

    /** 그 시각이 속한 주 */
    public static RankingWeek of(Instant at) {
        LocalDate day = at.atOffset(ZoneOffset.UTC).toLocalDate();
        return new RankingWeek(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    public String key() {
        return "%d-W%02d".formatted(monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** 이 주의 첫 순간(포함) */
    public Instant start() {
        return monday.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** 다음 주의 첫 순간(포함하지 않음) */
    public Instant end() {
        return monday.plusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

}
