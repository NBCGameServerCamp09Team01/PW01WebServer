package com.pw01.webserver.ranking;

import com.pw01.webserver.ranking.service.RankingClear;
import com.pw01.webserver.ranking.service.RankingRecordService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 랭킹 통합 시험 도우미. 랭킹은 단독으로 시험한다: 결과 저장(stageresult) 대신 RankingRecordService.recordClear를
 * 트랜잭션 안에서 바로 부르고, 계정은 account 표에 직접 넣는다(랭킹에 필요한 것은 외래 키와 닉네임뿐).
 */
final class RankingFixtures {

    static final String STAGE_1 = "stage.01.01";
    static final String STAGE_2 = "stage.01.02";
    static final String REGION_1 = "region.01";
    /** 시험 마스터의 권장 클리어 시간(stages.json, 두 스테이지 모두 300초) */
    static final long RECOMMENDED_MS = 300_000;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private RankingFixtures() {
    }

    /** 계정 한 줄. 아이디·닉네임은 실행 안에서 겹치지 않게 번호를 붙인다(prefix는 영문·숫자 12자 이하) */
    static long account(JdbcTemplate jdbc, String prefix) {
        String loginId = prefix + SEQUENCE.incrementAndGet();
        jdbc.update("INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)"
                + " VALUES (?, 'x', ?, NULL, 'ACTIVE', NOW(3), NOW(3))", loginId, "랭" + loginId);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

    static String nickname(JdbcTemplate jdbc, long accountId) {
        return jdbc.queryForObject("SELECT nickname FROM account WHERE account_id = ?", String.class, accountId);
    }

    /** 클리어 하나를 기록하고 커밋한다(커밋 뒤 Redis 갱신까지 돈다) */
    static String clear(TransactionTemplate tx, RankingRecordService service, long accountId, String stageId,
                        long playTimeMs, Instant at) {
        String stagePlayId = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> service.recordClear(
                new RankingClear(accountId, stagePlayId, stageId, 0, true, playTimeMs, at)));
        return stagePlayId;
    }

    /** 점수식(RankingScore, T 300초)으로 기대 점수 */
    static long score(long playTimeMs) {
        return 1000 + 9000 * Math.max(0, RECOMMENDED_MS - playTimeMs) / RECOMMENDED_MS;
    }

    /** 랭킹 표·Redis 랭킹 키를 비운다(시험끼리 DB·Redis를 함께 쓰므로 시험마다) */
    static void reset(JdbcTemplate jdbc, StringRedisTemplate redis) {
        jdbc.update("DELETE FROM stage_best_record");
        jdbc.update("DELETE FROM region_best_score");
        jdbc.update("DELETE FROM total_best_score");
        jdbc.update("DELETE FROM account_clear_progress");
        jdbc.update("DELETE FROM ranking_clear_log");
        Set<String> keys = redis.keys("pw01:rank*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    static Instant thisWeek(int plusMillis) {
        return com.pw01.webserver.ranking.service.RankingWeek.of(Instant.now()).start().plusSeconds(1)
                .plusMillis(plusMillis);
    }

}
