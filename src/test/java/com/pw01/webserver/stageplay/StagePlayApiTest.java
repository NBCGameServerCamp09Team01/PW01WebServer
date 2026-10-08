package com.pw01.webserver.stageplay;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스테이지 플레이 시작·현황(공유 초안 stage-play-api-draft-1009.md P1~P5)을 앱 전체 + 실제 MySQL·Redis로 확인한다.
 * 필수 시험: 중복 방지(같은 시작 요청, 동시 시작, 계정당 진행 중 하나), 예외(상태·코드), 스테이지 진행과의 흐름.
 * 실패하면 StagePlayService의 판단 순서, V5 stage_play의 유일 제약(uk_stage_play_one_in_progress), StagePlay 상태 함수,
 * RunResultService의 endWithResult 자리를 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(StagePlay01~).
 */
@IntegrationTest
class StagePlayApiTest {

    private static final String PASSWORD = "stageplaypass1";
    private static final String STAGE_1 = "stage.01.01";
    private static final String STAGE_2 = "stage.01.02";
    private static final String PLAYS = "/accounts/me/stage-plays";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    // 확인: 201 + Location, 응답 칸(주인 accountId 문자열, 진행 중, 난이도 0 = 조절 없음, 끝난 칸 null, 서버 웨이브 수)
    @Test
    void 시작하면_진행_중_플레이와_위치를_준다() throws Exception {
        String token = signupAndLogin("StagePlay01", "플레이01");
        String accountId = String.valueOf(accountId("StagePlay01"));

        ResultActions started = start(token, newId(), STAGE_1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value(accountId))
                .andExpect(jsonPath("$.data.stageId").value(STAGE_1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.difficulty").value(0))
                .andExpect(jsonPath("$.data.waveCount").value(5))
                .andExpect(jsonPath("$.data.endReason").value(nullValue()))
                .andExpect(jsonPath("$.data.endedAt").value(nullValue()));
        String id = idOf(started);
        started.andExpect(header().string(HttpHeaders.LOCATION, PLAYS + "/" + id));

        mockMvc.perform(get(PLAYS + "/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stagePlayId").value(id))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    // 확인(중복 방지): 같은 requestId 재전송 → 같은 플레이·행 하나, 같은 requestId에 다른 스테이지 → 409 IDEMPOTENCY_KEY_REUSED
    @Test
    void 같은_시작_요청은_같은_플레이() throws Exception {
        String token = signupAndLogin("StagePlay02", "플레이02");
        String requestId = newId();
        String first = idOf(start(token, requestId, STAGE_1).andExpect(status().isCreated()));
        String second = idOf(start(token, requestId, STAGE_1).andExpect(status().isCreated()));
        assertThat(second).isEqualTo(first);

        start(token, requestId, STAGE_2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.retryable").value(false));
        assertThat(count("SELECT COUNT(*) FROM stage_play WHERE account_id = ?", accountId("StagePlay02")))
                .isEqualTo(1);
    }

    // 확인(계정당 하나): 진행 중이면 409 → current로 받음 → 포기(FAILED·ABANDONED) → 포기 재전송 200 → 새로 시작 201
    @Test
    void 진행_중이면_시작이_막히고_포기하면_다시_시작한다() throws Exception {
        String token = signupAndLogin("StagePlay03", "플레이03");
        String first = idOf(start(token, newId(), STAGE_1).andExpect(status().isCreated()));

        start(token, newId(), STAGE_1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_IN_PROGRESS"))
                .andExpect(jsonPath("$.retryable").value(false));

        current(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stagePlayId").value(first));

        abandon(token, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.endReason").value("ABANDONED"))
                .andExpect(jsonPath("$.data.endedAt").value(not(nullValue())));
        abandon(token, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endReason").value("ABANDONED"));

        current(token).andExpect(status().isNoContent());
        start(token, newId(), STAGE_1).andExpect(status().isCreated());

        // 포기는 결과·보상을 남기지 않는다
        long accountId = accountId("StagePlay03");
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM run_result WHERE account_id = ?", accountId)).isZero();
    }

    // 확인(동시): 다른 requestId로 동시에 두 번 시작해도 진행 중은 하나. 하나는 201, 하나는 409
    @Test
    void 동시에_두_번_시작해도_진행_중은_하나() throws Exception {
        String token = signupAndLogin("StagePlay04", "플레이04");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> start(token, newId(), STAGE_1).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(count("SELECT COUNT(*) FROM stage_play WHERE account_id = ? AND status = 'IN_PROGRESS'",
                accountId("StagePlay04"))).isEqualTo(1);
    }

    // 확인(만료): 마감이 지난 진행 중 → current 204, 하나 조회는 EXPIRED, 포기는 409, 새로 시작 201
    @Test
    void 마감이_지난_진행_중은_만료되고_새로_시작된다() throws Exception {
        String token = signupAndLogin("StagePlay05", "플레이05");
        String id = idOf(start(token, newId(), STAGE_1).andExpect(status().isCreated()));
        jdbc.update("UPDATE stage_play SET expires_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE stage_play_id = ?", id);

        current(token).andExpect(status().isNoContent());
        mockMvc.perform(get(PLAYS + "/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.endReason").value("EXPIRED"));
        abandon(token, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_IN_PROGRESS"));
        start(token, newId(), STAGE_1).andExpect(status().isCreated());
    }

    // 확인(조회 규칙): 남의 플레이 404, 없는 ID 404, UUID 아님 400(stagePlayId), 토큰 없음 401
    @Test
    void 조회_규칙() throws Exception {
        String owner = signupAndLogin("StagePlay06", "플레이06");
        String other = signupAndLogin("StagePlay07", "플레이07");
        String id = idOf(start(owner, newId(), STAGE_1).andExpect(status().isCreated()));

        mockMvc.perform(get(PLAYS + "/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_FOUND"));
        abandon(other, id).andExpect(status().isNotFound());
        mockMvc.perform(get(PLAYS + "/{id}", newId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PLAYS + "/{id}", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("stagePlayId"));
        mockMvc.perform(get(PLAYS + "/current")).andExpect(status().isUnauthorized());
    }

    // 확인(결과와 맞물림): 결과 저장 → CLEARED·RESULT, 끝난 플레이 포기 409. 포기한 플레이에 결과 → 409, 결과·보상 없음
    @Test
    void 결과로_끝나고_포기한_플레이는_결과를_받지_않는다() throws Exception {
        String token = signupAndLogin("StagePlay08", "플레이08");
        String cleared = idOf(start(token, newId(), STAGE_1).andExpect(status().isCreated()));
        submitResult(token, cleared, true).andExpect(status().isOk());
        mockMvc.perform(get(PLAYS + "/{id}", cleared).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.status").value("CLEARED"))
                .andExpect(jsonPath("$.data.endReason").value("RESULT"));
        abandon(token, cleared)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_IN_PROGRESS"));

        String abandoned = idOf(start(token, newId(), STAGE_1).andExpect(status().isCreated()));
        abandon(token, abandoned).andExpect(status().isOk());
        submitResult(token, abandoned, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_IN_PROGRESS"));
        assertThat(count("SELECT COUNT(*) FROM run_result WHERE run_id = ?", abandoned)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId("StagePlay08")))
                .isEqualTo(1);
    }

    // 확인: 모든 계정의 진행 중 목록에 두 계정이 닉네임과 함께 나오고, 끝난 플레이는 빠진다. status는 IN_PROGRESS만
    @Test
    void 모든_계정의_진행_중_목록() throws Exception {
        String a = signupAndLogin("StagePlay09", "플레이09");
        String b = signupAndLogin("StagePlay10", "플레이10");
        String playA = idOf(start(a, newId(), STAGE_1).andExpect(status().isCreated()));
        String playB = idOf(start(b, newId(), STAGE_1).andExpect(status().isCreated()));
        abandon(b, playB).andExpect(status().isOk());

        mockMvc.perform(get("/stage-plays").param("status", "IN_PROGRESS").header(HttpHeaders.AUTHORIZATION, bearer(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].stagePlayId").value(hasItem(playA)))
                .andExpect(jsonPath("$.data.items[*].stagePlayId").value(not(hasItem(playB))))
                .andExpect(jsonPath("$.data.items[?(@.stagePlayId == '%s')].nickname".formatted(playA))
                        .value(hasItem("플레이09")));
        mockMvc.perform(get("/stage-plays").param("status", "CLEARED").header(HttpHeaders.AUTHORIZATION, bearer(a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        mockMvc.perform(get("/stage-plays")).andExpect(status().isUnauthorized());
    }

    // 확인(DB 제약): 같은 계정에 진행 중 두 줄을 직접 넣으면 유일 제약 위반(서비스를 거치지 않아도 지켜진다)
    @Test
    void 진행_중_두_줄은_DB가_막는다() throws Exception {
        signupAndLogin("StagePlay11", "플레이11");
        long accountId = accountId("StagePlay11");
        insertInProgress(accountId);
        assertThatThrownBy(() -> insertInProgress(accountId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // 확인(흐름): 새 계정 ST2(1 OPEN·2 LOCKED) → 2 시작 403 → 1 시작·클리어 → ST2(1 CLEARED·2 OPEN)
    //            → 같은 결과 재전송해도 진행 그대로 → 2 시작 201
    @Test
    void 스테이지_열림부터_결과_반영까지() throws Exception {
        String token = signupAndLogin("StagePlay12", "플레이12");
        mockMvc.perform(get("/accounts/me/stages").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.stages[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.stages[1].status").value("LOCKED"));

        start(token, newId(), STAGE_2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAGE_LOCKED"));
        current(token).andExpect(status().isNoContent());

        String play = idOf(start(token, newId(), STAGE_1).andExpect(status().isCreated()));
        submitResult(token, play, true).andExpect(status().isOk());
        mockMvc.perform(get("/accounts/me/stages").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.stages[0].status").value("CLEARED"))
                .andExpect(jsonPath("$.data.stages[1].status").value("OPEN"));

        submitResult(token, play, true).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM account_stage_progress WHERE account_id = ?",
                accountId("StagePlay12"))).isEqualTo(1);

        start(token, newId(), STAGE_2).andExpect(status().isCreated());
    }

    private ResultActions start(String token, String requestId, String stageId) throws Exception {
        return mockMvc.perform(post(PLAYS).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "stageId": "%s"}
                        """.formatted(requestId, stageId)));
    }

    private ResultActions current(String token) throws Exception {
        return mockMvc.perform(get(PLAYS + "/current").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions abandon(String token, String id) throws Exception {
        return mockMvc.perform(post(PLAYS + "/{id}/abandon", id).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    /** 결과 제출은 지금 결과 API 경로(c와 정하기 전) */
    private ResultActions submitResult(String token, String stagePlayId, boolean cleared) throws Exception {
        return mockMvc.perform(post("/runs/{runId}/result", stagePlayId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "stageId": "%s", "cleared": %s, "reachedWave": %d,
                         "totalWaveCount": 5, "playTimeSeconds": 1.5, "earnedGold": 10, "killCount": 1}
                        """.formatted(newId(), STAGE_1, cleared, cleared ? 5 : 2)));
    }

    private void insertInProgress(long accountId) {
        jdbc.update("""
                INSERT INTO stage_play (stage_play_id, account_id, stage_id, wave_count, difficulty, status,
                                        start_request_id, started_at, expires_at, created_at, updated_at)
                VALUES (?, ?, 'stage.01.01', 5, 0, 'IN_PROGRESS', ?, NOW(3), DATE_ADD(NOW(3), INTERVAL 1 DAY), NOW(3), NOW(3))
                """, newId(), accountId, newId());
    }

    private static String idOf(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.data.stagePlayId");
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
