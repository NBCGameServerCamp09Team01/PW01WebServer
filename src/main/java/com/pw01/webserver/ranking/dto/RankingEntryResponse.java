package com.pw01.webserver.ranking.dto;

import com.pw01.webserver.ranking.service.RankingBoardKind;
import com.pw01.webserver.ranking.service.RankingRow;

import java.time.Instant;

/**
 * 순위 한 줄(ranking-api.md "줄 모양"). 보드에 맞지 않는 칸은 null로 나간다(칸은 늘 있음).
 * 상세 값(RankingRow.detail): 스테이지 = 클리어 시간 ms → playTimeSeconds, 지역·전체 총합 = clearedStageCount,
 * 진행 = furthestStageId(clearedStageCount는 score와 같음).
 */
public record RankingEntryResponse(long rank, String accountId, String nickname, long score, Instant achievedAt,
                                   Double playTimeSeconds, Integer clearedStageCount, String furthestStageId) {

    public static RankingEntryResponse of(RankingBoardKind kind, long rank, RankingRow row, String nickname) {
        Detail detail = Detail.of(kind, row);
        return new RankingEntryResponse(rank, Long.toString(row.accountId()), nickname == null ? "" : nickname,
                row.score(), row.achievedAt(), detail.playTimeSeconds(), detail.clearedStageCount(),
                detail.furthestStageId());
    }

    /** 보드 종류에 맞는 상세 칸 셋 */
    public record Detail(Double playTimeSeconds, Integer clearedStageCount, String furthestStageId) {

        static final Detail NONE = new Detail(null, null, null);

        public static Detail of(RankingBoardKind kind, RankingRow row) {
            if (row == null) {
                return NONE;
            }
            return switch (kind) {
                case STAGE -> new Detail(Long.parseLong(row.detail()) / 1000.0, null, null);
                case REGION, TOTAL -> new Detail(null, Integer.parseInt(row.detail()), null);
                case PROGRESS -> new Detail(null, Math.toIntExact(row.score()), row.detail());
            };
        }
    }

}
