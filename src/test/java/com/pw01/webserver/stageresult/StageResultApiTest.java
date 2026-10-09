package com.pw01.webserver.stageresult;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결과 제출을 앱 전체 + 실제 MySQL·Redis로 확인한다(루트 docs/contracts/result-api.md). 스테이지 플레이를 시작한 뒤 그 ID로 낸다.
 * 목표 흐름 "스테이지 1 클리어 → 저장 → 2 열림 → 2 플레이"와 d의 P3 흐름(stage-lines-for-c-1008.md 6장)을 포함한다.
 * 필수 시험: 서버 판정(보상), 중복 방지(같은 플레이 두 번 → 한 번), 예외(상태·코드).
 * 실패하면 StageResultService 검사 순서, V5 마이그레이션, LevelCurve, d의 StageProgressService 호출 자리를 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(ResultFlow01~).
 */
@IntegrationTest
class StageResultApiTest {

    private static final String PASSWORD = "resultflowpass1";
    private static final String STAGE_1 = "stage.01.01";
    private static final String STAGE_2 = "stage.01.02";
    private static final String UUID_LOWER = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    // 확인(목표 흐름): 2는 잠김 → 1 시작·클리어 → 보상 Lv1→3 → 1 CLEARED·2 OPEN → 2 시작·클리어 → Lv4
    @Test
    void 스테이지1_클리어하면_2가_열리고_2를_플레이한다() throws Exception {
        String token = signupAndLogin("ResultFlow01", "결과흐름01");

        startPlay(token, newId(), STAGE_2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAGE_LOCKED"));

        String play1 = startPlayOk(token, STAGE_1);

        submit(token, play1, resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.stagePlayId").value(play1))
                .andExpect(jsonPath("$.data.result.stageId").value(STAGE_1))
                .andExpect(jsonPath("$.data.result.cleared").value(true))
                .andExpect(jsonPath("$.data.result.waveCount").value(5))
                .andExpect(jsonPath("$.data.result.reward.expGained").value(300))
                .andExpect(jsonPath("$.data.result.reward.levelBefore").value(1))
                .andExpect(jsonPath("$.data.result.reward.levelAfter").value(3))
                .andExpect(jsonPath("$.data.result.reward.statPointsGained").value(4))
                .andExpect(jsonPath("$.data.account.accountLevel").value(3))
                .andExpect(jsonPath("$.data.account.experience").value(50))
                .andExpect(jsonPath("$.data.account.statPoints").value(4))
                .andExpect(jsonPath("$.data.account.version").value(1));

        mockMvc.perform(get("/accounts/me/stages").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stages[0].status").value("CLEARED"))
                .andExpect(jsonPath("$.data.stages[1].status").value("OPEN"));

        String play2 = startPlayOk(token, STAGE_2);
        submit(token, play2, resultBody(newId(), STAGE_2, true, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.reward.levelBefore").value(3))
                .andExpect(jsonPath("$.data.result.reward.levelAfter").value(4))
                .andExpect(jsonPath("$.data.account.accountLevel").value(4));

        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.accountLevel").value(4))
                .andExpect(jsonPath("$.data.statPoints").value(6));
    }

    // 확인(중복 방지): 같은 플레이에 다른 requestId로 다시 내면 처음 결과를 돌려주고 계정·진행·변경 내역은 그대로
    @Test
    void 같은_플레이를_다시_내도_한_번만_반영() throws Exception {
        String token = signupAndLogin("ResultFlow02", "결과흐름02");
        String play = startPlayOk(token, STAGE_1);
        submit(token, play, resultBody(newId(), STAGE_1, true, 5)).andExpect(status().isOk());

        submit(token, play, resultBody(newId(), STAGE_1, false, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.cleared").value(true))
                .andExpect(jsonPath("$.data.result.reward.expGained").value(300))
                .andExpect(jsonPath("$.data.account.accountLevel").value(3))
                .andExpect(jsonPath("$.data.account.version").value(1));

        long accountId = accountId("ResultFlow02");
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM account_stage_progress WHERE account_id = ?", accountId)).isEqualTo(1);
        assertThat(count("SELECT total FROM account_stat_total WHERE account_id = ? AND stat_key = 'stage_play.played'",
                accountId)).isEqualTo(1);
    }

    // 확인(중복 방지·동시): 같은 플레이의 결과 둘이 동시에 와도 보상은 한 번(플레이 ID 기본 키 + 재시도)
    @Test
    void 같은_플레이_결과가_동시에_와도_한_번() throws Exception {
        String token = signupAndLogin("ResultFlow03", "결과흐름03");
        String play = startPlayOk(token, STAGE_1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> submit(token, play, resultBody(newId(), STAGE_1, true, 5))
                        .andReturn().getResponse().getStatus());
            }
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }

        long accountId = accountId("ResultFlow03");
        assertThat(count("SELECT total_experience FROM account_progress WHERE account_id = ?", accountId))
                .isEqualTo(300);
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isEqualTo(1);
    }

    // 확인: 같은 시작 requestId를 다시 보내면 같은 플레이 ID. ID는 UUID 소문자 36자
    @Test
    void 같은_시작_요청은_같은_플레이() throws Exception {
        String token = signupAndLogin("ResultFlow04", "결과흐름04");
        String requestId = newId();
        String first = JsonPath.read(startPlay(token, requestId, STAGE_1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stagePlayId").value(matchesPattern(UUID_LOWER)))
                .andExpect(jsonPath("$.data.waveCount").value(5))
                .andReturn().getResponse().getContentAsString(), "$.data.stagePlayId");
        String second = JsonPath.read(startPlay(token, requestId, STAGE_1).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.stagePlayId");
        assertThat(second).isEqualTo(first);
    }

    // 확인(검사): 스테이지 다름 400 MISMATCH, 도달 웨이브 waveCount+1 400 INVALID, 클리어인데 웨이브 덜 깸 400,
    //            시작 직후 1시간 플레이 400. 거절된 결과는 저장되지 않는다
    @Test
    void 이상한_결과는_거절() throws Exception {
        String token = signupAndLogin("ResultFlow05", "결과흐름05");
        String play = startPlayOk(token, STAGE_1);

        submit(token, play, resultBody(newId(), STAGE_2, true, 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_MISMATCH"))
                .andExpect(jsonPath("$.errors[0].field").value("stageId"))
                .andExpect(jsonPath("$.retryable").value(false));
        submit(token, play, resultBody(newId(), STAGE_1, false, 6))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("reachedWave"));
        submit(token, play, resultBody(newId(), STAGE_1, true, 4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_INVALID"));
        submit(token, play, """
                {"requestId": "%s", "stageId": "%s", "cleared": true, "reachedWave": 5, "totalWaveCount": 5,
                 "playTimeSeconds": 3600, "earnedGold": 0, "killCount": 0}
                """.formatted(newId(), STAGE_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("playTimeSeconds"));

        assertThat(count("SELECT COUNT(*) FROM stage_play_result WHERE stage_play_id = ?", play)).isZero();
    }

    // 확인: 남의 플레이 404, 없는 플레이 404, UUID가 아닌 ID 400, 대문자 ID는 같은 플레이로 받음
    @Test
    void 플레이_ID_규칙() throws Exception {
        String owner = signupAndLogin("ResultFlow06", "결과흐름06");
        String other = signupAndLogin("ResultFlow07", "결과흐름07");
        String play = startPlayOk(owner, STAGE_1);

        submit(other, play, resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_FOUND"));
        submit(owner, newId(), resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isNotFound());
        submit(owner, "not-a-uuid", resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("stagePlayId"));
        submit(owner, play.toUpperCase(Locale.ROOT), resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.stagePlayId").value(play));
    }

    // 확인: 유효 시간(24시간)이 지난 플레이는 409 STAGE_PLAY_EXPIRED, retryable false
    @Test
    void 만료된_플레이는_409() throws Exception {
        String token = signupAndLogin("ResultFlow08", "결과흐름08");
        String play = startPlayOk(token, STAGE_1);
        jdbc.update("UPDATE stage_play SET expires_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE stage_play_id = ?", play);

        submit(token, play, resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_EXPIRED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    // 확인(보상 전 상태 검사): EXPIRED로 저장된 플레이 → 409 STAGE_PLAY_EXPIRED, 포기한 플레이 → 409 STAGE_PLAY_NOT_IN_PROGRESS.
    //            둘 다 retryable false, 보상·변경 내역·누적 통계·결과가 하나도 남지 않는다(보상 계산 전에 끊김)
    @Test
    void 끝난_플레이는_보상_전에_거절() throws Exception {
        String token = signupAndLogin("ResultFlow11", "결과흐름11");
        String expired = startPlayOk(token, STAGE_1);
        jdbc.update("UPDATE stage_play SET expires_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE stage_play_id = ?",
                expired);
        mockMvc.perform(get("/accounts/me/stage-plays/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent()); // 여기서 EXPIRED로 저장됨
        submit(token, expired, resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_EXPIRED"))
                .andExpect(jsonPath("$.retryable").value(false));

        String abandoned = startPlayOk(token, STAGE_1);
        mockMvc.perform(post("/accounts/me/stage-plays/{id}/abandon", abandoned)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        submit(token, abandoned, resultBody(newId(), STAGE_1, false, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_IN_PROGRESS"))
                .andExpect(jsonPath("$.retryable").value(false));

        long accountId = accountId("ResultFlow11");
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_stat_total WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM stage_play_result WHERE account_id = ?", accountId)).isZero();
    }

    // 확인: 실패한 플레이는 경험치 60, 진행 행 없음(스테이지 2는 여전히 잠김). 변경 내역 합 = 총 경험치
    @Test
    void 실패한_플레이와_합계_대조() throws Exception {
        String token = signupAndLogin("ResultFlow09", "결과흐름09");
        submit(token, startPlayOk(token, STAGE_1), resultBody(newId(), STAGE_1, false, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.reward.expGained").value(60))
                .andExpect(jsonPath("$.data.account.accountLevel").value(1));
        submit(token, startPlayOk(token, STAGE_1), resultBody(newId(), STAGE_1, true, 5))
                .andExpect(status().isOk());

        long accountId = accountId("ResultFlow09");
        assertThat(count("SELECT COALESCE(SUM(exp_delta), 0) FROM account_ledger WHERE account_id = ?", accountId))
                .isEqualTo(count("SELECT total_experience FROM account_progress WHERE account_id = ?", accountId))
                .isEqualTo(360);
        assertThat(count("SELECT COALESCE(SUM(point_delta), 0) FROM account_ledger WHERE account_id = ?", accountId))
                .isEqualTo(count("SELECT stat_points FROM account_progress WHERE account_id = ?", accountId));
    }

    // 확인: 플레이 시작 난이도는 0만(그 밖은 400), 토큰 없으면 401
    @Test
    void 플레이_시작_형식() throws Exception {
        String token = signupAndLogin("ResultFlow10", "결과흐름10");
        mockMvc.perform(post("/accounts/me/stage-plays").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "stageId": "%s", "difficulty": 1}
                                """.formatted(newId(), STAGE_1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/accounts/me/stage-plays").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "stageId": "%s"}
                                """.formatted(newId(), STAGE_1)))
                .andExpect(status().isUnauthorized());
        startPlay(token, newId(), "stage.99.99")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAGE_NOT_FOUND"));
    }

    private String startPlayOk(String token, String stageId) throws Exception {
        String body = startPlay(token, newId(), stageId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.stagePlayId");
    }

    private ResultActions startPlay(String token, String requestId, String stageId) throws Exception {
        return mockMvc.perform(post("/accounts/me/stage-plays").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "stageId": "%s"}
                        """.formatted(requestId, stageId)));
    }

    private ResultActions submit(String token, String stagePlayId, String body) throws Exception {
        return mockMvc.perform(post("/accounts/me/stage-plays/{stagePlayId}/result", stagePlayId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 플레이 시간 1초(시작 직후라도 경과 + 여유 60초 안) */
    private static String resultBody(String requestId, String stageId, boolean cleared, int reachedWave) {
        return """
                {"requestId": "%s", "stageId": "%s", "difficulty": 0, "cleared": %s, "reachedWave": %d,
                 "totalWaveCount": 5, "playTimeSeconds": 1.5, "earnedGold": 120, "killCount": 10}
                """.formatted(requestId, stageId, cleared, reachedWave);
    }

    private long accountId(String loginId) {
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

    private long count(String sql, Object arg) {
        Long value = jdbc.queryForObject(sql, Long.class, arg);
        return value == null ? 0 : value;
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
