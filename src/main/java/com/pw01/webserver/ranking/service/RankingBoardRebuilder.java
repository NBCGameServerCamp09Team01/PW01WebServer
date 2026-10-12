package com.pw01.webserver.ranking.service;

import com.pw01.webserver.config.RankingProperties;
import com.pw01.webserver.ranking.repository.RankingBoardQuery;
import com.pw01.webserver.ranking.repository.RankingRedisStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 보드를 MySQL(원천)에서 다시 채운다(redis-keys.md 랭킹 절 "명령 순서").
 * 잠금(rank_build_start) → 임시 보드에 MySQL 줄을 묶음으로(rank_build_add) → 통째로 바꿔 끼움(rank_build_finish).
 * MySQL을 읽기 전에 임시 보드를 만들어 두므로, 그사이 커밋된 결과는 rank_update가 임시 보드에도 넣는다(빠짐 없음).
 * <p>
 * "다시 채울 보드" 표시: 커밋 뒤 Redis 갱신이 실패한 보드를 이 서버 메모리에 적어 둔다. 다음에 그 보드를 쓸 때 다시 채우고,
 * 그 전까지 이 서버는 MySQL로 답한다. 서버가 다시 뜨면 표시가 사라진다(알고 고른 한계, ranking-api.md).
 * Redis·MySQL 오류는 DataAccessException 그대로 던진다(부른 쪽이 MySQL로 대신하거나 503).
 */
@Component
@RequiredArgsConstructor
public class RankingBoardRebuilder {

    private static final Logger log = LoggerFactory.getLogger(RankingBoardRebuilder.class);

    private final RankingRedisStore redisStore;
    private final RankingBoardQuery boardQuery;
    private final RankingProperties properties;

    private final Set<String> staleBoards = ConcurrentHashMap.newKeySet();

    public void markStale(RankingBoard board) {
        if (staleBoards.add(board.id())) {
            log.warn("랭킹 보드를 다시 채울 보드로 표시 board={}", board.id());
        }
    }

    public boolean isStale(RankingBoard board) {
        return staleBoards.contains(board.id());
    }

    /**
     * 보드를 다시 채운다.
     *
     * @return 바꿔 끼웠으면 true. 다른 서버가 채우는 중이거나 임시 보드·잠금을 잃었으면 false(부른 쪽은 MySQL로 대신)
     */
    public boolean rebuild(RankingBoard board) {
        String token = UUID.randomUUID().toString();
        if (!redisStore.buildStart(board, token, properties.rebuildLockTtl().toSeconds(),
                properties.buildTtl().toSeconds())) {
            log.info("랭킹 보드를 다른 서버가 채우는 중 board={}", board.id());
            return false;
        }
        long startedNanos = System.nanoTime();
        AtomicBoolean alive = new AtomicBoolean(true);
        boardQuery.forEachBatch(board, properties.buildBatchSize(), batch -> {
            if (alive.get() && !redisStore.buildAdd(board, batch)) {
                alive.set(false);
            }
        });
        boolean finished = alive.get() && redisStore.buildFinish(board, token, expireAt(board));
        long millis = (System.nanoTime() - startedNanos) / 1_000_000;
        if (finished) {
            staleBoards.remove(board.id());
            log.info("랭킹 보드 다시 채움 board={} {}ms", board.id(), millis);
        } else {
            log.warn("랭킹 보드 다시 채우기를 끝내지 못함(임시 보드 만료 또는 잠금 잃음) board={} {}ms", board.id(), millis);
        }
        return finished;
    }

    long expireAt(RankingBoard board) {
        return board.expireAtEpochSeconds(Instant.now(), properties.allBoardTtl(), properties.weeklyRetention());
    }

}
