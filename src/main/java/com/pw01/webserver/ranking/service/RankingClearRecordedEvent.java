package com.pw01.webserver.ranking.service;

import java.util.List;

/**
 * 클리어 하나가 랭킹 표(MySQL)에 기록됐다. 결과 트랜잭션이 **커밋된 뒤** RankingCacheUpdater가 Redis 보드에 옮긴다.
 * 트랜잭션이 되돌아가면(재시도 포함) 리스너가 받지 않으므로 Redis에 들어가지 않는다.
 *
 * @param accountId        계정 ID
 * @param updates          보드마다 넣을 줄(더 좋을 때만 바뀜)
 * @param weeklySumBoards  주간 합 보드(지역·전체 총합). 그 주 합은 커밋 뒤 MySQL에서 읽어 넣는다
 */
public record RankingClearRecordedEvent(long accountId, List<BoardUpdate> updates, List<RankingBoard> weeklySumBoards) {

    public RankingClearRecordedEvent {
        updates = List.copyOf(updates);
        weeklySumBoards = List.copyOf(weeklySumBoards);
    }

    /** 보드 하나에 넣을 줄 */
    public record BoardUpdate(RankingBoard board, RankingRow row) {
    }

}
