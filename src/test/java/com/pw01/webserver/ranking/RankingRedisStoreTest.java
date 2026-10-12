package com.pw01.webserver.ranking;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.ranking.repository.RankingRedisStore;
import com.pw01.webserver.ranking.repository.RankingRedisStore.Mine;
import com.pw01.webserver.ranking.repository.RankingRedisStore.Page;
import com.pw01.webserver.ranking.service.RankingBoard;
import com.pw01.webserver.ranking.service.RankingPeriod;
import com.pw01.webserver.ranking.service.RankingRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 Lua 스크립트 6개를 실제 Redis 7.4로 확인한다(redis-keys.md v1.3 랭킹 절, src/main/resources/redis/rank_*.lua).
 * 실패하면 스크립트 본문과 명세 본문이 다른지, RankingRow.member()·zScore() 모양이 바뀌었는지 의심한다.
 */
@IntegrationTest
class RankingRedisStoreTest {

    private static final Instant AT = Instant.parse("2026-10-12T06:00:00.000Z");
    private static final long EXPIRE = AT.plusSeconds(3600).getEpochSecond() + 10_000_000L;

    @Autowired
    RankingRedisStore store;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JdbcTemplate jdbc;

    private final RankingBoard board = RankingBoard.stage("stage.01.01", 0, RankingPeriod.ALL, AT);

    @BeforeEach
    void setUp() {
        RankingFixtures.reset(jdbc, redis);
    }

    // 확인: 보드가 없으면 넣지 않음(-1), page·me도 null(→ 부른 쪽이 다시 채움)
    @Test
    void 보드가_없으면_넣지_않는다() {
        assertThat(store.update(board, row(1, 5000, 0, 1), EXPIRE)).isEqualTo(-1);
        assertThat(redis.hasKey(board.redisKey())).isFalse();
        assertThat(store.page(board, null, 10)).isNull();
        assertThat(store.me(board, 1)).isNull();
    }

    // 확인: 다시 채우기 시작·묶음·끝, 빈 보드도 "있음"(@), 만료가 걸림
    @Test
    void 다시_채우기() {
        assertThat(store.buildStart(board, "t1", 30, 60)).isTrue();
        assertThat(store.buildStart(board, "t2", 30, 60)).isFalse();             // 잠금 경합
        assertThat(store.buildAdd(board, List.of(row(1, 5000, 0, 1), row(2, 7000, 1, 2)))).isTrue();
        assertThat(store.buildFinish(board, "t2", EXPIRE)).isFalse();           // 남의 토큰
        assertThat(store.buildFinish(board, "t1", EXPIRE)).isTrue();
        assertThat(redis.hasKey(board.lockKey())).isFalse();
        assertThat(redis.getExpire(board.redisKey())).isPositive();

        Page page = store.page(board, null, 10);
        assertThat(page.start()).isZero();
        assertThat(page.rows()).extracting(RankingRow::accountId).containsExactly(2L, 1L);

        RankingBoard empty = RankingBoard.stage("stage.01.02", 0, RankingPeriod.ALL, AT);
        assertThat(store.buildStart(empty, "t", 30, 60)).isTrue();
        assertThat(store.buildFinish(empty, "t", EXPIRE)).isTrue();
        assertThat(store.page(empty, null, 10).rows()).isEmpty();               // null이 아니라 빈 목록
        assertThat(store.me(empty, 1).total()).isZero();
    }

    // 확인: 더 좋을 때만 바뀜(점수 → 먼저 달성 → 먼저 기록), 계정당 한 줄
    @Test
    void 더_좋을_때만_바뀐다() {
        build(row(1, 5000, 10, 10));
        assertThat(store.update(board, row(1, 4000, 20, 20), EXPIRE)).isZero();   // 낮음
        assertThat(store.update(board, row(1, 5000, 30, 30), EXPIRE)).isZero();   // 같은 점수, 늦음
        assertThat(store.update(board, row(1, 6000, 40, 40), EXPIRE)).isEqualTo(1);
        assertThat(redis.opsForZSet().zCard(board.redisKey())).isEqualTo(1);
        Mine mine = store.me(board, 1);
        assertThat(mine.rank()).isEqualTo(1);
        assertThat(mine.row().score()).isEqualTo(6000);
        assertThat(mine.row().clearSeq()).isEqualTo(40);
    }

    // 확인: 다시 채우는 중(임시 보드)에 온 갱신은 임시 보드에도 들어가 바꿔 끼운 뒤에도 남는다
    @Test
    void 다시_채우는_중의_갱신() {
        assertThat(store.buildStart(board, "t", 30, 60)).isTrue();
        assertThat(store.buildAdd(board, List.of(row(1, 5000, 0, 1)))).isTrue();
        assertThat(store.update(board, row(2, 9000, 5, 5), EXPIRE)).isEqualTo(1);   // 커밋 뒤 갱신이 임시 보드로
        assertThat(store.buildFinish(board, "t", EXPIRE)).isTrue();
        assertThat(store.page(board, null, 10).rows()).extracting(RankingRow::accountId).containsExactly(2L, 1L);
    }

    // 확인: 커서 = 값. 커서 줄이 그사이 바뀌어도(그 계정 기록이 오름) 값으로 자리를 센다
    @Test
    void 커서는_값으로_자리를_센다() {
        build(row(1, 9000, 0, 1), row(2, 8000, 0, 2), row(3, 8000, 0, 3), row(4, 7000, 0, 4));
        RankingRow cursor = row(2, 8000, 0, 2);
        Page next = store.page(board, cursor, 10);
        assertThat(next.start()).isEqualTo(2);
        assertThat(next.rows()).extracting(RankingRow::accountId).containsExactly(3L, 4L);

        store.update(board, row(2, 9500, 9, 9), EXPIRE);                         // 커서 줄이 사라짐
        Page after = store.page(board, cursor, 10);
        assertThat(after.rows()).extracting(RankingRow::accountId).containsExactly(3L, 4L);
        assertThat(after.start()).isEqualTo(2);                                  // 9500·9000이 앞 = 2줄
    }

    private void build(RankingRow... rows) {
        assertThat(store.buildStart(board, "seed", 30, 60)).isTrue();
        assertThat(store.buildAdd(board, List.of(rows))).isTrue();
        assertThat(store.buildFinish(board, "seed", EXPIRE)).isTrue();
    }

    private static RankingRow row(long accountId, long score, int plusMillis, long clearSeq) {
        return new RankingRow(accountId, score, AT.plusMillis(plusMillis), clearSeq, "100000");
    }

}
