package com.pw01.webserver.ranking;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.pw01.webserver.ranking.RankingFixtures.REGION_1;
import static com.pw01.webserver.ranking.RankingFixtures.STAGE_1;
import static com.pw01.webserver.ranking.RankingFixtures.score;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결과 저장 → 랭킹 연동을 앱 전체 + 실제 MySQL·Redis로 확인한다. RankingApiTest(랭킹 단독, recordClear를 바로 부름)와 달리
 * 실제 API(플레이 시작 → 결과 제출 → 랭킹 조회)를 거친다. StageResultService.submitOnce 6번이 RankingRecordService.recordClear를 부른다.
 * 필수 시험: 서버 판정(점수는 서버가 저장한 플레이 시간으로), 중복 방지(같은 플레이 두 번·동시 → 랭킹 한 번), 실패 결과는 기록 안 함.
 * 실패하면 StageResultService 6번(호출 자리·넘기는 값), RankingRecordService, RankingCacheUpdater(커밋 뒤 Redis)를 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(RkLink01~).
 */
@IntegrationTest
class RankingResultLinkTest {

    private static final String PASSWORD = "rankinglink1";
    /** 결과 본문의 플레이 시간 1.5초(시작 직후라도 경과 + 여유 60초 안) */
    private static final long PLAY_TIME_MS = 1_500;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        RankingFixtures.reset(jdbc, redis);
    }

    // 확인: 클리어 결과 하나 → 스테이지·지역·전체 총합·진행 보드와 내 순위에 들어간다.
    //       점수는 서버가 저장한 플레이 시간으로 계산하고, 달성 시각은 결과 응답의 submittedAt(결과 저장 시각)과 같다
    @Test
    void 클리어_결과가_랭킹에_들어간다() throws Exception {
        String token = signupAndLogin("RkLink01", "랭크연동01");
        String accountId = Long.toString(accountId("RkLink01"));
        String play = startPlayOk(token, STAGE_1);
        String body = submit(token, play, resultBody(STAGE_1, true, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("SAVED"))
                .andReturn().getResponse().getContentAsString();
        String submittedAt = JsonPath.read(body, "$.data.result.submittedAt");

        query(token, "/rankings/stages/" + STAGE_1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].accountId").value(accountId))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].score").value(score(PLAY_TIME_MS)))
                .andExpect(jsonPath("$.data.items[0].playTimeSeconds").value(1.5))
                .andExpect(jsonPath("$.data.items[0].achievedAt").value(submittedAt));
        query(token, "/accounts/me/rankings/stages/" + STAGE_1)
                .andExpect(jsonPath("$.data.rank").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(1));
        query(token, "/rankings/regions/" + REGION_1)
                .andExpect(jsonPath("$.data.items[0].accountId").value(accountId))
                .andExpect(jsonPath("$.data.items[0].score").value(score(PLAY_TIME_MS)))
                .andExpect(jsonPath("$.data.items[0].clearedStageCount").value(1));
        query(token, "/rankings/total")
                .andExpect(jsonPath("$.data.items[0].accountId").value(accountId))
                .andExpect(jsonPath("$.data.items[0].score").value(score(PLAY_TIME_MS)));
        query(token, "/rankings/progress")
                .andExpect(jsonPath("$.data.items[0].accountId").value(accountId))
                .andExpect(jsonPath("$.data.items[0].score").value(1))
                .andExpect(jsonPath("$.data.items[0].furthestStageId").value(STAGE_1));
        // 이번 주 보드에도 같은 줄(결과 저장 시각이 이번 주)
        query(token, "/rankings/stages/" + STAGE_1 + "?period=weekly")
                .andExpect(jsonPath("$.data.items[0].accountId").value(accountId));
    }

    // 확인: 실패 결과는 랭킹에 남지 않고, 같은 플레이를 다시 내도(ALREADY_SAVED) 랭킹 기록은 한 번
    @Test
    void 실패와_다시_낸_결과는_기록하지_않는다() throws Exception {
        String token = signupAndLogin("RkLink02", "랭크연동02");
        long accountId = accountId("RkLink02");

        String failed = startPlayOk(token, STAGE_1);
        submit(token, failed, resultBody(STAGE_1, false, 2)).andExpect(status().isOk());
        assertThat(clearLogCount(accountId)).isZero();
        query(token, "/accounts/me/rankings/stages/" + STAGE_1)
                .andExpect(jsonPath("$.data.rank").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0));

        String play = startPlayOk(token, STAGE_1);
        submit(token, play, resultBody(STAGE_1, true, 5))
                .andExpect(jsonPath("$.data.saveStatus").value("SAVED"));
        submit(token, play, resultBody(STAGE_1, true, 5))
                .andExpect(jsonPath("$.data.saveStatus").value("ALREADY_SAVED"));
        assertThat(clearLogCount(accountId)).isEqualTo(1);
        query(token, "/rankings/stages/" + STAGE_1)
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    // 확인(동시): 같은 플레이의 결과 둘이 동시에 와도 랭킹 기록은 한 번. 뒤 트랜잭션은 결과 기본 키에서 되돌아가고
    //            (랭킹 기록도 함께 되돌아감) 다시 할 때 처음 결과를 받는다. 둘이면 랭킹이 결과와 다른 트랜잭션에서 커밋된 것
    @Test
    void 같은_플레이_결과가_동시에_와도_랭킹은_한_번() throws Exception {
        String token = signupAndLogin("RkLink03", "랭크연동03");
        long accountId = accountId("RkLink03");
        String play = startPlayOk(token, STAGE_1);

        List<String> saveStatuses = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> submit(token, play, resultBody(STAGE_1, true, 5))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
            }
            for (Future<String> f : pool.invokeAll(calls)) {
                saveStatuses.add(JsonPath.read(f.get(), "$.data.saveStatus"));
            }
        } finally {
            pool.shutdown();
        }
        assertThat(saveStatuses).containsExactlyInAnyOrder("SAVED", "ALREADY_SAVED");
        assertThat(clearLogCount(accountId)).isEqualTo(1);
        query(token, "/accounts/me/rankings/stages/" + STAGE_1)
                .andExpect(jsonPath("$.data.rank").value(1));
    }

    private long clearLogCount(long accountId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM ranking_clear_log WHERE account_id = ?", Long.class,
                accountId);
        return value == null ? 0 : value;
    }

    private ResultActions query(String token, String pathAndQuery) throws Exception {
        return mockMvc.perform(get(pathAndQuery).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String startPlayOk(String token, String stageId) throws Exception {
        String body = mockMvc.perform(post("/accounts/me/stage-plays").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "stageId": "%s"}
                                """.formatted(newId(), stageId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.stagePlayId");
    }

    private ResultActions submit(String token, String stagePlayId, String body) throws Exception {
        return mockMvc.perform(post("/accounts/me/stage-plays/{stagePlayId}/result", stagePlayId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 요청마다 새 requestId. 플레이 시간은 PLAY_TIME_MS와 같은 1.5초 */
    private static String resultBody(String stageId, boolean cleared, int reachedWave) {
        return """
                {"requestId": "%s", "stageId": "%s", "difficulty": 0, "cleared": %s, "reachedWave": %d,
                 "totalWaveCount": 5, "playTimeSeconds": 1.5, "earnedGold": 120, "killCount": 10}
                """.formatted(newId(), stageId, cleared, reachedWave);
    }

    private long accountId(String loginId) {
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String bearer(String token) {
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
