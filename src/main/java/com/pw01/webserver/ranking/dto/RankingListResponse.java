package com.pw01.webserver.ranking.dto;

import java.util.List;

/**
 * RK1·RK3·RK5 순위 목록(ranking-api.md). nextCursor는 hasNext가 false면 null(칸은 늘 있음).
 */
public record RankingListResponse(RankingBoardResponse board, List<RankingEntryResponse> items, boolean hasNext,
                                  String nextCursor) {
}
