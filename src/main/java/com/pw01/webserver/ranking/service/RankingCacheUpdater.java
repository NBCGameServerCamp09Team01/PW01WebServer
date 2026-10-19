package com.pw01.webserver.ranking.service;

import com.pw01.webserver.config.RankingProperties;
import com.pw01.webserver.ranking.repository.RankingBoardQuery;
import com.pw01.webserver.ranking.repository.RankingRedisStore;
import com.pw01.webserver.ranking.service.RankingClearRecordedEvent.BoardUpdate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * 결과 트랜잭션이 커밋된 뒤 Redis 보드를 고친다(rank_update: 더 좋을 때만). 롤백된 기록은 들어가지 않는다.
 * 보드마다 따로 시도하고, 실패하면 경고 로그 + "다시 채울 보드" 표시만 한다. 예외를 밖으로 던지지 않는다
 * (커밋 뒤 콜백의 예외는 결과 응답까지 올라가는데, 결과는 이미 저장되어 200이어야 한다).
 */
@Component
@RequiredArgsConstructor
public class RankingCacheUpdater {

    private static final Logger log = LoggerFactory.getLogger(RankingCacheUpdater.class);

    private final RankingRedisStore redisStore;
    private final RankingBoardQuery boardQuery;
    private final RankingBoardRebuilder rebuilder;
    private final RankingProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClearRecorded(RankingClearRecordedEvent event) {
        Instant now = Instant.now();
        for (BoardUpdate update : event.updates()) {
            apply(update.board(), update.row(), now);
        }
        // 주간 합(지역·전체 총합)은 그 주 기록(커밋됨)에서 읽는다. 이번 기록이 그 주 스테이지 최고를 바꾸지 않았으면 Redis가 그대로 둔다
        for (RankingBoard weekly : event.weeklySumBoards()) {
            try {
                boardQuery.findRow(weekly, event.accountId()).ifPresent(row -> apply(weekly, row, now));
            } catch (RuntimeException e) {
                log.warn("주간 합 줄을 읽지 못함 board={} accountId={}", weekly.id(), event.accountId(), e);
                rebuilder.markStale(weekly);
            }
        }
    }

    private void apply(RankingBoard board, RankingRow row, Instant now) {
        try {
            redisStore.update(board, row,
                    board.expireAtEpochSeconds(now, properties.allBoardTtl(), properties.weeklyRetention()));
        } catch (RuntimeException e) {
            log.warn("랭킹 Redis 갱신 실패 board={} accountId={} cause={}", board.id(), row.accountId(),
                    e.getClass().getSimpleName(), e);
            rebuilder.markStale(board);
        }
    }

}
