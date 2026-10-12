package com.pw01.webserver.ranking.dto;

import com.pw01.webserver.ranking.dto.RankingEntryResponse.Detail;
import com.pw01.webserver.ranking.service.RankingBoard;
import com.pw01.webserver.ranking.service.RankingRow;

import java.time.Instant;

/**
 * RK2·RK4·RK6 내 줄(ranking-api.md). 그 보드에 내 기록이 없으면 rank부터 상세까지 null(칸은 늘 있음).
 */
public record MyRankingResponse(RankingBoardResponse board, Long rank, Long score, Instant achievedAt,
                                Double playTimeSeconds, Integer clearedStageCount, String furthestStageId,
                                long totalCount) {

    public static MyRankingResponse of(RankingBoard board, Long rank, RankingRow row, long totalCount) {
        Detail detail = Detail.of(board.kind(), row);
        return new MyRankingResponse(RankingBoardResponse.from(board), row == null ? null : rank,
                row == null ? null : row.score(), row == null ? null : row.achievedAt(),
                detail.playTimeSeconds(), detail.clearedStageCount(), detail.furthestStageId(), totalCount);
    }

}
