package com.pw01.webserver.ranking.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 보드의 한 줄(한 계정). DB·Redis·커서가 같은 값을 쓴다.
 * 순서: score 높은 쪽 → achievedAt 이른 쪽 → clearSeq 작은 쪽(ranking-api.md "보드와 순서").
 *
 * @param accountId  계정 ID
 * @param score      보드 점수(스테이지 점수, 지역 합, 진행 스테이지 수)
 * @param achievedAt 달성 시각(밀리초까지)
 * @param clearSeq   기록 순번(ranking_clear_log.clear_seq)
 * @param detail     상세: 스테이지 = 클리어 시간 ms, 지역 = 클리어한 스테이지 수, 진행 = 가장 먼 스테이지 키
 */
public record RankingRow(long accountId, long score, Instant achievedAt, long clearSeq, String detail) {

    public RankingRow {
        Objects.requireNonNull(achievedAt);
        Objects.requireNonNull(detail);
        if (detail.indexOf('|') >= 0) {
            throw new IllegalArgumentException("상세 값에 '|'를 쓸 수 없습니다(커서 구분자): " + detail);
        }
    }

    /**
     * Redis 멤버: <달성 epoch ms 13자리>:<순번 20자리>:<계정 ID>:<상세>.
     * 앞 두 칸의 자리 수가 고정이라 같은 점수끼리의 문자 순서 = 먼저 달성 → 먼저 기록(redis-keys.md 랭킹 절).
     */
    public String member() {
        return "%013d:%020d:%d:%s".formatted(achievedAt.toEpochMilli(), clearSeq, accountId, detail);
    }

    /** Redis ZSet 점수 = −보드 점수(오름차순으로 읽으면 높은 점수가 먼저) */
    public double zScore() {
        return -score;
    }

    /** Redis 멤버·ZSet 점수에서 다시 만든다. 모양이 틀리면 IllegalArgumentException */
    public static RankingRow fromMember(String member, double zScore) {
        String[] parts = member == null ? new String[0] : member.split(":", 4);
        if (parts.length != 4 || parts[0].length() != 13 || parts[1].length() != 20) {
            throw new IllegalArgumentException("랭킹 멤버 모양이 아닙니다: " + member);
        }
        try {
            return new RankingRow(Long.parseLong(parts[2]), Math.round(-zScore),
                    Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]), parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("랭킹 멤버 모양이 아닙니다: " + member, e);
        }
    }

}
