package com.pw01.webserver.ranking;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.ranking.service.RankingBoard;
import com.pw01.webserver.ranking.service.RankingClear;
import com.pw01.webserver.ranking.service.RankingPeriod;
import com.pw01.webserver.ranking.service.RankingRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.pw01.webserver.ranking.RankingFixtures.REGION_1;
import static com.pw01.webserver.ranking.RankingFixtures.STAGE_1;
import static com.pw01.webserver.ranking.RankingFixtures.STAGE_2;
import static com.pw01.webserver.ranking.RankingFixtures.account;
import static com.pw01.webserver.ranking.RankingFixtures.clear;
import static com.pw01.webserver.ranking.RankingFixtures.nickname;
import static com.pw01.webserver.ranking.RankingFixtures.score;
import static com.pw01.webserver.ranking.RankingFixtures.thisWeek;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 랭킹을 앱 전체 + 실제 MySQL·Redis로 확인한다(루트 docs/contracts/ranking-api.md v1, redis-keys.md v1.3 랭킹 절).
 * 랭킹 단독 시험: 결과 API를 거치지 않고 RankingRecordService.recordClear를 트랜잭션 안에서 바로 부른다(결과 API를 거치는 연동은 RankingResultLinkTest).
 * 필수 시험: 서버 판정(점수·덮어쓰기·순서), 예외(상태·코드). Redis·MySQL 두 길이 같은 답을 내는지도 본다.
 * 실패하면 V8 마이그레이션·RankingRecordRepository(덮어쓰기 SQL)·RankingBoardQuery(순서·커서 조건)·rank_*.lua를 의심한다.
 */
@IntegrationTest
class RankingApiTest {

    private static final String PASSWORD = "rankingpass1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    RankingRecordService recordService;

    private String token;
    private long viewerId;

    @BeforeEach
    void setUp() throws Exception {
        RankingFixtures.reset(jdbc, redis);
        String loginId = "RkView" + UUID.randomUUID().toString().substring(0, 8);
        token = signupAndLogin(loginId, "보는이" + loginId.substring(6));
        viewerId = jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

    // 확인: 순서 = 점수 높은 쪽 → 먼저 달성 → 같은 ms면 먼저 기록. 응답 칸(점수·시간·닉네임·board)
    @Test
    void 순서는_점수_달성_기록순() throws Exception {
        long fast = account(jdbc, "RkOrd");
        long first = account(jdbc, "RkOrd");
        long second = account(jdbc, "RkOrd");
        Instant at = thisWeek(0);
        clear(tx, recordService, second, STAGE_1, 200_000, at);           // 같은 점수·같은 ms, 먼저 기록
        clear(tx, recordService, first, STAGE_1, 200_000, at);            // 같은 점수·같은 ms, 나중 기록
        clear(tx, recordService, fast, STAGE_1, 100_000, at.plusMillis(5));

        list("/rankings/stages/" + STAGE_1, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.board.kind").value("STAGE"))
                .andExpect(jsonPath("$.data.board.targetId").value(STAGE_1))
                .andExpect(jsonPath("$.data.board.difficulty").value(0))
                .andExpect(jsonPath("$.data.board.period").value("all"))
                .andExpect(jsonPath("$.data.board.periodKey").value("all"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].accountId").value(Long.toString(fast)))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].score").value(score(100_000)))
                .andExpect(jsonPath("$.data.items[0].playTimeSeconds").value(100.0))
                .andExpect(jsonPath("$.data.items[0].nickname").value(nickname(jdbc, fast)))
                .andExpect(jsonPath("$.data.items[0].clearedStageCount").isEmpty())
                .andExpect(jsonPath("$.data.items[1].accountId").value(Long.toString(second)))
                .andExpect(jsonPath("$.data.items[2].accountId").value(Long.toString(first)))
                .andExpect(jsonPath("$.data.items[2].rank").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());
    }

    // 확인: 최고 점수가 오를 때만 덮어씀. 낮은 점수·같은 점수의 늦은 기록은 그대로, 클리어 횟수는 늘 +1
    @Test
    void 최고_기록은_오를_때만_덮어쓴다() throws Exception {
        long player = account(jdbc, "RkBest");
        Instant at = thisWeek(0);
        clear(tx, recordService, player, STAGE_1, 200_000, at);
        clear(tx, recordService, player, STAGE_1, 250_000, at.plusMillis(1));   // 낮음
        clear(tx, recordService, player, STAGE_1, 200_000, at.plusMillis(2));   // 같음(늦음)
        assertBest(player, score(200_000), at);
        clear(tx, recordService, player, STAGE_1, 150_000, at.plusMillis(3));   // 오름
        assertBest(player, score(150_000), at.plusMillis(3));
        assertThat(jdbc.queryForObject("SELECT clear_count FROM stage_best_record WHERE account_id = ?",
                Integer.class, player)).isEqualTo(4);

        me("/accounts/me/rankings/stages/" + STAGE_1, "")
                .andExpect(jsonPath("$.data.rank").isEmpty());   // 보는 계정은 기록 없음
        list("/rankings/stages/" + STAGE_1, "")
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].score").value(score(150_000)))
                .andExpect(jsonPath("$.data.items[0].achievedAt").value(at.plusMillis(3).toString()));
    }

    // 확인: 지역 = 스테이지 최고의 합, 진행 = 처음 클리어한 스테이지 수(같은 스테이지 다시 클리어는 그대로)
    @Test
    void 지역_합과_진행() throws Exception {
        long player = account(jdbc, "RkRegion");
        Instant at = thisWeek(0);
        clear(tx, recordService, player, STAGE_1, 200_000, at);
        clear(tx, recordService, player, STAGE_2, 100_000, at.plusMillis(10));
        clear(tx, recordService, player, STAGE_1, 250_000, at.plusMillis(20));  // 낮음: 지역·진행 그대로

        list("/rankings/regions/" + REGION_1, "")
                .andExpect(jsonPath("$.data.board.kind").value("REGION"))
                .andExpect(jsonPath("$.data.items[0].score").value(score(200_000) + score(100_000)))
                .andExpect(jsonPath("$.data.items[0].clearedStageCount").value(2))
                .andExpect(jsonPath("$.data.items[0].achievedAt").value(at.plusMillis(10).toString()))
                .andExpect(jsonPath("$.data.items[0].playTimeSeconds").isEmpty());
        list("/rankings/progress", "")
                .andExpect(jsonPath("$.data.board.kind").value("PROGRESS"))
                .andExpect(jsonPath("$.data.board.targetId").isEmpty())
                .andExpect(jsonPath("$.data.board.difficulty").isEmpty())
                .andExpect(jsonPath("$.data.items[0].score").value(2))
                .andExpect(jsonPath("$.data.items[0].clearedStageCount").value(2))
                .andExpect(jsonPath("$.data.items[0].furthestStageId").value(STAGE_2))
                .andExpect(jsonPath("$.data.items[0].achievedAt").value(at.plusMillis(10).toString()));
        // 주간 지역도 같은 합(모두 이번 주)
        list("/rankings/regions/" + REGION_1, "&period=weekly")
                .andExpect(jsonPath("$.data.items[0].score").value(score(200_000) + score(100_000)))
                .andExpect(jsonPath("$.data.items[0].clearedStageCount").value(2));
    }

    // 확인: 커서 경계(정확히 20, 21~25), 다음 묶음 첫 순위, 묶음 사이에 기록이 끼어도 겹침·빠짐 없음
    @Test
    void 커서_경계와_끼어든_기록() throws Exception {
        List<Long> players = new ArrayList<>();
        Instant at = thisWeek(0);
        for (int i = 0; i < 25; i++) {
            long player = account(jdbc, "RkPage");
            players.add(player);
            clear(tx, recordService, player, STAGE_1, 100_000 + i * 1000L, at.plusMillis(i));   // i가 작을수록 위
        }
        String first = list("/rankings/stages/" + STAGE_1, "")
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[19].accountId").value(Long.toString(players.get(19))))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");

        // 끼어든 1위
        long newcomer = account(jdbc, "RkPage");
        clear(tx, recordService, newcomer, STAGE_1, 10_000, at.plusMillis(100));

        list("/rankings/stages/" + STAGE_1, "&cursor=" + cursor)
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].accountId").value(Long.toString(players.get(20))))
                .andExpect(jsonPath("$.data.items[0].rank").value(22))      // 끼어든 1명만큼 밀림
                .andExpect(jsonPath("$.data.items[4].rank").value(26))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());

        // 정확히 limit줄이면 hasNext false
        list("/rankings/stages/" + STAGE_1, "&limit=26")
                .andExpect(jsonPath("$.data.items.length()").value(26))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    // 확인: 내 순위(목록 밖), 기록 없으면 null 칸 + totalCount
    @Test
    void 내_순위() throws Exception {
        Instant at = thisWeek(0);
        for (int i = 0; i < 3; i++) {
            clear(tx, recordService, account(jdbc, "RkMe"), STAGE_1, 100_000 + i, at);
        }
        me("/accounts/me/rankings/stages/" + STAGE_1, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").isEmpty())
                .andExpect(jsonPath("$.data.score").isEmpty())
                .andExpect(jsonPath("$.data.achievedAt").isEmpty())
                .andExpect(jsonPath("$.data.playTimeSeconds").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(3));
        clear(tx, recordService, viewerId, STAGE_1, 290_000, at.plusMillis(1));
        me("/accounts/me/rankings/stages/" + STAGE_1, "")
                .andExpect(jsonPath("$.data.rank").value(4))
                .andExpect(jsonPath("$.data.score").value(score(290_000)))
                .andExpect(jsonPath("$.data.playTimeSeconds").value(290.0))
                .andExpect(jsonPath("$.data.totalCount").value(4));
        me("/accounts/me/rankings/regions/" + REGION_1, "")
                .andExpect(jsonPath("$.data.rank").value(4))
                .andExpect(jsonPath("$.data.clearedStageCount").value(1));
        me("/accounts/me/rankings/progress", "")
                .andExpect(jsonPath("$.data.rank").value(4))
                .andExpect(jsonPath("$.data.furthestStageId").value(STAGE_1));
    }

    // 확인: 주간은 그 주 기록만. 지난주 기록은 전체에만 있다
    @Test
    void 주간은_이번_주만() throws Exception {
        long lastWeek = account(jdbc, "RkWeek");
        long thisWeekPlayer = account(jdbc, "RkWeek");
        clear(tx, recordService, lastWeek, STAGE_1, 50_000, thisWeek(0).minus(Duration.ofDays(2)));
        clear(tx, recordService, thisWeekPlayer, STAGE_1, 200_000, thisWeek(0));
        list("/rankings/stages/" + STAGE_1, "&period=weekly")
                .andExpect(jsonPath("$.data.board.period").value("weekly"))
                .andExpect(jsonPath("$.data.board.periodKey").value(matchesWeekKey()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].accountId").value(Long.toString(thisWeekPlayer)));
        list("/rankings/stages/" + STAGE_1, "&period=all")
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].accountId").value(Long.toString(lastWeek)));
    }

    // 확인: 쿼리 값 400(VALIDATION_FAILED + 칸 이름), 없는 키 404, 커서 400
    @Test
    void 오류() throws Exception {
        list("/rankings/stages/" + STAGE_1, "&period=monthly").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("period"));
        list("/rankings/stages/" + STAGE_1, "&limit=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        list("/rankings/stages/" + STAGE_1, "&limit=51").andExpect(status().isBadRequest());
        list("/rankings/stages/" + STAGE_1, "&limit=abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        list("/rankings/stages/" + STAGE_1, "&difficulty=1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("difficulty"));
        list("/rankings/progress", "&period=weekly").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("period"));
        list("/rankings/stages/stage.99.99", "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAGE_NOT_FOUND"));
        list("/rankings/regions/region.99", "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_NOT_FOUND"));
        list("/rankings/stages/" + STAGE_1, "&cursor=bad!cursor").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANK_INVALID_CURSOR"))
                .andExpect(jsonPath("$.retryable").value(false));

        // 다른 보드(스테이지 2)의 커서
        Instant at = thisWeek(0);
        for (int i = 0; i < 2; i++) {
            clear(tx, recordService, account(jdbc, "RkErr"), STAGE_2, 100_000 + i, at);
        }
        String body = list("/rankings/stages/" + STAGE_2, "&limit=1").andReturn().getResponse().getContentAsString();
        String otherCursor = JsonPath.read(body, "$.data.nextCursor");
        list("/rankings/stages/" + STAGE_1, "&cursor=" + otherCursor).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANK_INVALID_CURSOR"));
        // 인증 없음
        mockMvc.perform(get("/rankings/stages/" + STAGE_1)).andExpect(status().isUnauthorized());
    }

    // 확인: Redis 보드를 지운 뒤 다시 채워도 같은 답, 랭킹 Redis 명령이 실패해도 MySQL로 같은 답(부분 장애)
    @Test
    void Redis를_지우거나_깨져도_같은_답() throws Exception {
        Instant at = thisWeek(0);
        for (int i = 0; i < 5; i++) {
            clear(tx, recordService, account(jdbc, "RkFall"), STAGE_1, 100_000 + (i % 2) * 1000L, at.plusMillis(i % 3));
        }
        String fromRedis = items(list("/rankings/stages/" + STAGE_1, ""));
        RankingBoard board = RankingBoard.stage(STAGE_1, 0, RankingPeriod.ALL, at);
        assertThat(redis.hasKey(board.memberKey())).isTrue();   // 첫 조회가 보드를 채웠다

        redis.delete(List.of(board.redisKey(), board.memberKey()));
        assertThat(items(list("/rankings/stages/" + STAGE_1, ""))).isEqualTo(fromRedis);

        // 보드 키를 다른 타입으로 덮어 랭킹 명령만 실패하게 한다(WRONGTYPE) → MySQL로 대신
        redis.delete(board.memberKey());
        redis.opsForHash().put(board.memberKey(), "@", "1");
        redis.delete(board.redisKey());
        redis.opsForValue().set(board.redisKey(), "broken");
        assertThat(items(list("/rankings/stages/" + STAGE_1, ""))).isEqualTo(fromRedis);
        me("/accounts/me/rankings/stages/" + STAGE_1, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5));
        redis.delete(List.of(board.redisKey(), board.memberKey()));
    }

    // 확인: 커밋된 기록만 Redis에 들어간다(되돌린 트랜잭션은 MySQL·Redis 모두 없음), 커밋되면 다시 채우지 않아도 바로 보인다
    @Test
    void 커밋_뒤에만_Redis에_들어간다() throws Exception {
        long kept = account(jdbc, "RkCommit");
        long rolledBack = account(jdbc, "RkCommit");
        Instant at = thisWeek(0);
        clear(tx, recordService, kept, STAGE_1, 200_000, at);
        list("/rankings/stages/" + STAGE_1, "").andExpect(jsonPath("$.data.items.length()").value(1));   // 보드 채움

        tx.executeWithoutResult(status -> {
            recordService.recordClear(new RankingClear(rolledBack, UUID.randomUUID().toString(), STAGE_1, 0, true,
                    50_000, at.plusMillis(1)));
            status.setRollbackOnly();
        });
        RankingBoard board = RankingBoard.stage(STAGE_1, 0, RankingPeriod.ALL, at);
        assertThat(redis.opsForZSet().zCard(board.redisKey())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_clear_log WHERE account_id = ?", Long.class,
                rolledBack)).isZero();

        clear(tx, recordService, rolledBack, STAGE_1, 50_000, at.plusMillis(2));
        assertThat(redis.opsForZSet().zCard(board.redisKey())).isEqualTo(2);   // 다시 채우지 않고 커밋 뒤 갱신으로
        list("/rankings/stages/" + STAGE_1, "")
                .andExpect(jsonPath("$.data.items[0].accountId").value(Long.toString(rolledBack)));
    }

    // 확인: 실패 판은 아무것도 남기지 않고, 트랜잭션 밖 호출은 막는다(MANDATORY)
    @Test
    void 실패_판과_트랜잭션_밖() {
        long player = account(jdbc, "RkFail");
        tx.executeWithoutResult(status -> recordService.recordClear(
                new RankingClear(player, UUID.randomUUID().toString(), STAGE_1, 0, false, 100_000, thisWeek(0))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_clear_log WHERE account_id = ?", Long.class,
                player)).isZero();
        assertThatThrownBy(() -> recordService.recordClear(
                new RankingClear(player, UUID.randomUUID().toString(), STAGE_1, 0, true, 100_000, thisWeek(0))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // 확인: 전체 총합(RK7·RK8) = 모든 스테이지 최고의 합, 주간도 같은 규칙, Redis를 지운 뒤 다시 채워도 같은 답
    @Test
    void 전체_총합() throws Exception {
        long both = account(jdbc, "RkTotal");
        long one = account(jdbc, "RkTotal");
        Instant at = thisWeek(0);
        clear(tx, recordService, both, STAGE_1, 200_000, at);
        clear(tx, recordService, both, STAGE_2, 250_000, at.plusMillis(10));
        clear(tx, recordService, one, STAGE_1, 10_000, at.plusMillis(20));          // 한 스테이지만, 그래도 아래
        clear(tx, recordService, viewerId, STAGE_2, 290_000, at.plusMillis(30));

        long bothTotal = score(200_000) + score(250_000);
        String body = items(list("/rankings/total", "")
                .andExpect(jsonPath("$.data.board.kind").value("TOTAL"))
                .andExpect(jsonPath("$.data.board.targetId").isEmpty())
                .andExpect(jsonPath("$.data.board.difficulty").value(0))
                .andExpect(jsonPath("$.data.items[0].accountId").value(Long.toString(score(10_000) > bothTotal ? one : both)))
                .andExpect(jsonPath("$.data.items.length()").value(3)));
        list("/rankings/total", "")
                .andExpect(jsonPath("$.data.items[?(@.accountId == '" + both + "')].score").value(org.hamcrest.Matchers.contains((int) bothTotal)))
                .andExpect(jsonPath("$.data.items[?(@.accountId == '" + both + "')].clearedStageCount").value(org.hamcrest.Matchers.contains(2)));
        me("/accounts/me/rankings/total", "")
                .andExpect(jsonPath("$.data.board.kind").value("TOTAL"))
                .andExpect(jsonPath("$.data.rank").value(3))
                .andExpect(jsonPath("$.data.score").value(score(290_000)))
                .andExpect(jsonPath("$.data.clearedStageCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(3));
        // 주간 전체 총합(모두 이번 주) = 전체 기간과 같은 순서
        assertThat(items(list("/rankings/total", "&period=weekly"))).isEqualTo(body);

        RankingBoard board = RankingBoard.total(0, RankingPeriod.ALL, at);
        redis.delete(List.of(board.redisKey(), board.memberKey()));
        assertThat(items(list("/rankings/total", ""))).isEqualTo(body);
        list("/rankings/total", "&difficulty=1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("difficulty"));
    }

    private void assertBest(long accountId, long expectedScore, Instant expectedAt) {
        assertThat(jdbc.queryForObject("SELECT score FROM stage_best_record WHERE account_id = ?", Long.class,
                accountId)).isEqualTo(expectedScore);
        assertThat(jdbc.queryForObject("SELECT achieved_at FROM stage_best_record WHERE account_id = ?",
                java.time.LocalDateTime.class, accountId).toInstant(java.time.ZoneOffset.UTC)).isEqualTo(expectedAt);
    }

    private static org.hamcrest.Matcher<String> matchesWeekKey() {
        return org.hamcrest.Matchers.matchesPattern("^\\d{4}-W\\d{2}$");
    }

    private static String items(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.items").toString();
    }

    private ResultActions list(String path, String query) throws Exception {
        return mockMvc.perform(get(path + "?" + query.replaceFirst("^&", ""))
                .header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    private ResultActions me(String path, String query) throws Exception {
        return list(path, query);
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String signupAndLogin(String loginId, String nickname) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s", "nickname": "%s"}
                                """.formatted(loginId, PASSWORD, nickname)))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(loginId, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

}
