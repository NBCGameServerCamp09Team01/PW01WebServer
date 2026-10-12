package com.pw01.webserver.ranking.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 보드 이름·주 계산·멤버 모양을 확인한다(redis-keys.md 랭킹 절). 키 이름이 바뀌면 운영 Redis의 보드를 못 찾으므로 명세와 같아야 한다.
 */
class RankingBoardTest {

    private static final Instant SUNDAY_LAST = Instant.parse("2026-10-18T23:59:59.999Z");
    private static final Instant MONDAY_FIRST = Instant.parse("2026-10-19T00:00:00.000Z");

    // 확인: 주는 월요일 00:00 UTC 시작(일요일 끝 ms와 월요일 0ms가 다른 주)
    @Test
    void 주_경계() {
        assertThat(RankingWeek.of(SUNDAY_LAST).key()).isEqualTo("2026-W42");
        assertThat(RankingWeek.of(MONDAY_FIRST).key()).isEqualTo("2026-W43");
        assertThat(RankingWeek.of(MONDAY_FIRST).start()).isEqualTo(MONDAY_FIRST);
        assertThat(RankingWeek.of(SUNDAY_LAST).end()).isEqualTo(MONDAY_FIRST);
    }

    // 확인: 연말·연초는 ISO 주 기준 연도(2027-01-01은 2026-W53)
    @Test
    void 연말_주() {
        assertThat(RankingWeek.of(Instant.parse("2027-01-01T12:00:00Z")).key()).isEqualTo("2026-W53");
        assertThat(RankingWeek.of(Instant.parse("2027-01-04T00:00:00Z")).key()).isEqualTo("2027-W01");
        assertThatThrownBy(() -> new RankingWeek(LocalDate.of(2026, 10, 20))).isInstanceOf(IllegalArgumentException.class);
    }

    // 확인: 키 이름(명세 표와 같음)
    @Test
    void 키_이름() {
        RankingBoard stageAll = RankingBoard.stage("stage.01.01", 0, RankingPeriod.ALL, MONDAY_FIRST);
        assertThat(stageAll.redisKey()).isEqualTo("pw01:rank:stage:stage.01.01:d0:all");
        assertThat(stageAll.memberKey()).isEqualTo("pw01:rank:stage:stage.01.01:d0:all:member");
        assertThat(stageAll.buildKey()).isEqualTo("pw01:rank:stage:stage.01.01:d0:all:build");
        assertThat(stageAll.buildMemberKey()).isEqualTo("pw01:rank:stage:stage.01.01:d0:all:build:member");
        assertThat(stageAll.lockKey()).isEqualTo("pw01:rank-lock:stage:stage.01.01:d0:all");
        assertThat(stageAll.periodKey()).isEqualTo("all");

        RankingBoard regionWeek = RankingBoard.region("region.01", 0, RankingPeriod.WEEKLY, SUNDAY_LAST);
        assertThat(regionWeek.redisKey()).isEqualTo("pw01:rank:region:region.01:d0:week:2026-W42");
        assertThat(regionWeek.periodKey()).isEqualTo("2026-W42");

        assertThat(RankingBoard.progress().redisKey()).isEqualTo("pw01:rank:progress:all");
        assertThat(RankingBoard.total(0, RankingPeriod.ALL, MONDAY_FIRST).redisKey()).isEqualTo("pw01:rank:total:d0:all");
        assertThat(RankingBoard.total(0, RankingPeriod.WEEKLY, SUNDAY_LAST).redisKey())
                .isEqualTo("pw01:rank:total:d0:week:2026-W42");
    }

    // 확인: 만료 시각 = 전체는 지금 + 30일, 주간은 그 주 끝 + 14일
    @Test
    void 만료_시각() {
        Duration all = Duration.ofDays(30);
        Duration weekly = Duration.ofDays(14);
        RankingBoard stageAll = RankingBoard.stage("stage.01.01", 0, RankingPeriod.ALL, SUNDAY_LAST);
        assertThat(stageAll.expireAtEpochSeconds(SUNDAY_LAST, all, weekly))
                .isEqualTo(SUNDAY_LAST.plus(all).getEpochSecond());
        RankingBoard stageWeek = RankingBoard.stage("stage.01.01", 0, RankingPeriod.WEEKLY, SUNDAY_LAST);
        assertThat(stageWeek.expireAtEpochSeconds(SUNDAY_LAST, all, weekly))
                .isEqualTo(MONDAY_FIRST.plus(weekly).getEpochSecond());
    }

    // 확인: 멤버 모양과 되읽기. 같은 점수면 문자 순서 = 먼저 달성 → 먼저 기록
    @Test
    void 멤버() {
        RankingRow row = new RankingRow(12, 4498, Instant.ofEpochMilli(1_760_249_400_123L), 42, "183400");
        assertThat(row.member()).isEqualTo("1760249400123:00000000000000000042:12:183400");
        assertThat(row.zScore()).isEqualTo(-4498.0);
        assertThat(RankingRow.fromMember(row.member(), row.zScore())).isEqualTo(row);

        RankingRow earlier = new RankingRow(99, 4498, Instant.ofEpochMilli(1_760_249_400_122L), 50, "1");
        RankingRow sameMsLaterSeq = new RankingRow(1, 4498, Instant.ofEpochMilli(1_760_249_400_123L), 43, "1");
        assertThat(earlier.member()).isLessThan(row.member());
        assertThat(row.member()).isLessThan(sameMsLaterSeq.member());

        assertThatThrownBy(() -> RankingRow.fromMember("12:34", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    // 확인: 커서 왕복, 다른 보드·틀린 값은 RANK_INVALID_CURSOR
    @Test
    void 커서() {
        RankingBoard board = RankingBoard.stage("stage.01.01", 0, RankingPeriod.WEEKLY, SUNDAY_LAST);
        RankingRow row = new RankingRow(12, 4498, Instant.ofEpochMilli(1_760_249_400_123L), 42, "183400");
        String cursor = RankingCursor.encode(board, row);
        assertThat(cursor.length()).isLessThanOrEqualTo(200);
        assertThat(RankingCursor.decode(board, cursor)).isEqualTo(row);

        RankingBoard nextWeek = RankingBoard.stage("stage.01.01", 0, RankingPeriod.WEEKLY, MONDAY_FIRST);
        assertInvalid(nextWeek, cursor);
        assertInvalid(RankingBoard.stage("stage.01.02", 0, RankingPeriod.WEEKLY, SUNDAY_LAST), cursor);
        assertInvalid(board, "not-base64-!!");
        assertInvalid(board, "");
        assertInvalid(board, "a".repeat(201));
    }

    private static void assertInvalid(RankingBoard board, String cursor) {
        assertThatThrownBy(() -> RankingCursor.decode(board, cursor))
                .isInstanceOfSatisfying(com.pw01.webserver.common.error.InvalidRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo(RankingErrors.RANK_INVALID_CURSOR));
    }

}
