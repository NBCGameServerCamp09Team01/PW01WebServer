package com.pw01.webserver.ranking.repository;

import com.pw01.webserver.ranking.service.RankingBoard;
import com.pw01.webserver.ranking.service.RankingRow;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 랭킹 보드의 Redis 접근(pw01:rank:*, pw01:rank-lock:*). 키·스크립트는 루트 docs/contracts/redis-keys.md v1.3 랭킹 절이 기준이다.
 * 스크립트 본문은 src/main/resources/redis/rank_*.lua(명세의 본문과 같아야 한다).
 * Redis 오류는 DataAccessException 그대로 던진다. 랭킹은 사본이라 부른 쪽(RankingQueryService·RankingCacheUpdater)이 받아 MySQL로 대신한다.
 * {@code @Component}인 이유는 RedisLoginFailStore와 같다(IllegalArgumentException이 DataAccessException으로 바뀌지 않게).
 */
@Component
@RequiredArgsConstructor
public class RankingRedisStore {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> PAGE = script("rank_page", List.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ME = script("rank_me", List.class);
    private static final DefaultRedisScript<Long> UPDATE = script("rank_update", Long.class);
    private static final DefaultRedisScript<Long> BUILD_START = script("rank_build_start", Long.class);
    private static final DefaultRedisScript<Long> BUILD_ADD = script("rank_build_add", Long.class);
    private static final DefaultRedisScript<Long> BUILD_FINISH = script("rank_build_finish", Long.class);

    private final StringRedisTemplate redis;

    /** 목록 한 묶음. 보드가 없으면 null */
    public record Page(long start, List<RankingRow> rows) {
    }

    /** 내 줄. 보드가 없으면 null, 내 기록이 없으면 row·rank가 null */
    public record Mine(long total, Long rank, RankingRow row) {
    }

    /**
     * rank_update: 보드(있으면)·임시 보드(다시 채우는 중이면)에 더 좋을 때만 넣는다.
     *
     * @return 1 바꿈, 0 그대로, -1 보드가 없어 넣지 않음
     */
    public long update(RankingBoard board, RankingRow row, long expireAtEpochSeconds) {
        Long result = redis.execute(UPDATE, boardKeys(board),
                Long.toString(row.accountId()), row.member(), score(row), Long.toString(expireAtEpochSeconds));
        return result == null ? -1 : result;
    }

    /**
     * rank_page: 커서(마지막 줄) 다음부터 readCount줄. cursor가 null이면 처음부터.
     *
     * @return 보드가 없으면 null. start는 첫 줄의 0부터 위치(순위 = start + 1)
     */
    public Page page(RankingBoard board, RankingRow cursor, int readCount) {
        List<?> result = redis.execute(PAGE, List.of(board.redisKey(), board.memberKey()),
                Integer.toString(readCount), cursor == null ? "" : score(cursor), cursor == null ? "" : cursor.member());
        if (boardMissing(result)) {
            return null;
        }
        long start = ((Number) result.get(0)).longValue();
        List<?> flat = (List<?>) result.get(1);
        List<RankingRow> rows = new ArrayList<>(flat.size() / 2);
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            rows.add(RankingRow.fromMember((String) flat.get(i), Double.parseDouble((String) flat.get(i + 1))));
        }
        return new Page(start, rows);
    }

    /** rank_me. 보드가 없으면 null */
    public Mine me(RankingBoard board, long accountId) {
        List<?> result = redis.execute(ME, List.of(board.redisKey(), board.memberKey()), Long.toString(accountId));
        if (boardMissing(result)) {
            return null;
        }
        long total = ((Number) result.get(0)).longValue();
        if (result.size() < 4) {
            return new Mine(total, null, null);
        }
        long rank = ((Number) result.get(1)).longValue();
        RankingRow row = RankingRow.fromMember((String) result.get(2), Double.parseDouble((String) result.get(3)));
        return new Mine(total, rank + 1, row);
    }

    /** rank_build_start: 잠금을 얻고 빈 임시 보드를 만든다. 다른 서버가 채우는 중이면 false */
    public boolean buildStart(RankingBoard board, String token, long lockSeconds, long buildSeconds) {
        Long result = redis.execute(BUILD_START, List.of(board.lockKey(), board.buildKey(), board.buildMemberKey()),
                token, Long.toString(lockSeconds), Long.toString(buildSeconds));
        return result != null && result == 1;
    }

    /** rank_build_add: 임시 보드에 묶음을 넣는다. 임시 보드가 사라졌으면 false */
    public boolean buildAdd(RankingBoard board, List<RankingRow> rows) {
        if (rows.isEmpty()) {
            return true;
        }
        List<String> args = new ArrayList<>(rows.size() * 3);
        for (RankingRow row : rows) {
            args.add(Long.toString(row.accountId()));
            args.add(row.member());
            args.add(score(row));
        }
        Long result = redis.execute(BUILD_ADD, List.of(board.buildKey(), board.buildMemberKey()), args.toArray());
        return result != null && result >= 0;
    }

    /** rank_build_finish: 잠금이 내 것이고 임시 보드가 살아 있으면 보드를 바꿔 끼운다 */
    public boolean buildFinish(RankingBoard board, String token, long expireAtEpochSeconds) {
        Long result = redis.execute(BUILD_FINISH, List.of(board.redisKey(), board.memberKey(), board.buildKey(),
                board.buildMemberKey(), board.lockKey()), token, Long.toString(expireAtEpochSeconds));
        return result != null && result == 1;
    }

    /**
     * 스크립트가 "보드 없음"(Lua false → nil)을 돌려줬나. 목록 결과형이면 Spring이 nil을 null 하나가 든 목록으로 줄 수 있어
     * null·빈 목록·첫 칸 null을 모두 "없음"으로 본다.
     */
    private static boolean boardMissing(List<?> result) {
        return result == null || result.isEmpty() || result.getFirst() == null;
    }

    private static List<String> boardKeys(RankingBoard board) {
        return List.of(board.redisKey(), board.memberKey(), board.buildKey(), board.buildMemberKey());
    }

    /** ZSet 점수(−보드 점수). 정수라 소수점 없이 넘긴다 */
    private static String score(RankingRow row) {
        return Long.toString(-row.score());
    }

    private static <T> DefaultRedisScript<T> script(String name, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/" + name + ".lua"));
        script.setResultType(resultType);
        return script;
    }

}
