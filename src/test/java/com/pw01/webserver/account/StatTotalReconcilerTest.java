package com.pw01.webserver.account;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.account.service.StatTotalReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 누적 통계 대조(StatTotalReconciler)를 실제 MySQL로 확인한다: 결과 표(원천)와 어긋난 누적 통계를 결과 표 값으로 고친다.
 * 다른 테스트가 jdbc로 넣은 누적 통계도 같은 DB에 있으므로, 고친 줄 수 대신 이 계정의 값만 본다.
 * 실패하면 StageResultTotalsQuery의 집계 SQL, StatTotalReconciler의 키 대응·replaceIf 조건을 의심한다.
 */
@IntegrationTest
class StatTotalReconcilerTest {

    private static final String PASSWORD = "reconcilepass1";
    private static final String STAGE_1 = "stage.01.01";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StatTotalReconciler reconciler;

    // 확인: 합계를 틀리게(처치 999), 최고 기록 줄을 지우고(최고 클리어), 최고 기록을 틀리게(웨이브 0) 해도 대조 뒤 결과 표 값.
    //       어긋남이 없는 키(골드·플레이 시간)는 그대로, 한 번 더 대조해도 같은 값
    @Test
    void 어긋난_누적_통계를_결과_표_값으로_고친다() throws Exception {
        String token = signupAndLogin("Reconcile01", "대조01");
        long accountId = accountId("Reconcile01");
        submit(token, startPlayOk(token), resultBody(true, 5, 1.5, 7));
        submit(token, startPlayOk(token), resultBody(false, 3, 2.0, 2));

        jdbc.update("UPDATE account_stat_total SET total = 999 WHERE account_id = ? AND stat_key = 'stage_play.kills'",
                accountId);
        jdbc.update("DELETE FROM account_stat_total WHERE account_id = ? AND stat_key = 'stage_play.best_clear_ms'",
                accountId);
        jdbc.update("UPDATE account_stat_total SET total = 0 WHERE account_id = ? AND stat_key = 'stage_play.max_wave'",
                accountId);

        int fixed = reconciler.reconcileAll();

        assertThat(fixed).isGreaterThanOrEqualTo(3);
        assertThat(total(accountId, "stage_play.kills")).isEqualTo(9);
        assertThat(total(accountId, "stage_play.best_clear_ms")).isEqualTo(1500);
        assertThat(total(accountId, "stage_play.max_wave")).isEqualTo(5);
        assertThat(total(accountId, "stage_play.gold")).isEqualTo(240);
        assertThat(total(accountId, "stage_play.play_time_ms")).isEqualTo(3500);
        reconciler.reconcileAll();
        assertThat(total(accountId, "stage_play.kills")).isEqualTo(9);
    }

    // 확인: 결과가 없는 계정에 누적 통계가 있으면(합계는 0으로, 최고 기록 줄은 지움)
    @Test
    void 결과가_없는_계정의_누적_통계는_비운다() throws Exception {
        signupAndLogin("Reconcile02", "대조02");
        long accountId = accountId("Reconcile02");
        jdbc.update("""
                INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
                VALUES (?, 'stage_play.kills', 5, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (?, 'stage_play.max_wave', 4, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))""", accountId, accountId);

        reconciler.reconcileAll();

        assertThat(total(accountId, "stage_play.kills")).isZero();
        assertThat(total(accountId, "stage_play.max_wave")).as("줄 없음").isEqualTo(-1);
    }

    private String startPlayOk(String token) throws Exception {
        String body = mockMvc.perform(post("/accounts/me/stage-plays").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "stageId": "%s"}
                                """.formatted(UUID.randomUUID(), STAGE_1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.stagePlayId");
    }

    private void submit(String token, String stagePlayId, String body) throws Exception {
        mockMvc.perform(post("/accounts/me/stage-plays/{stagePlayId}/result", stagePlayId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private static String resultBody(boolean cleared, int reachedWave, double playTimeSeconds, int kills) {
        return """
                {"requestId": "%s", "stageId": "%s", "difficulty": 0, "cleared": %s, "reachedWave": %d,
                 "totalWaveCount": 5, "playTimeSeconds": %s, "earnedGold": 120, "killCount": %d}
                """.formatted(UUID.randomUUID(), STAGE_1, cleared, reachedWave, playTimeSeconds, kills);
    }

    /** 줄이 없으면 -1 */
    private long total(long accountId, String key) {
        Long value = jdbc.query("SELECT total FROM account_stat_total WHERE account_id = ? AND stat_key = ?",
                rs -> rs.next() ? rs.getLong(1) : null, accountId, key);
        return value == null ? -1 : value;
    }

    private long accountId(String loginId) {
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
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
