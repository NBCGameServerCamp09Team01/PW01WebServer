package com.pw01.webserver.stageplay;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스테이지 플레이 시작·현황(공유 초안 stage-play-api-draft-1009.md P1~P5)을 앱 전체 + 실제 MySQL·Redis로 확인한다.
 * 필수 시험: 중복 방지(같은 시작 요청, 동시 시작, 계정당 진행 중 하나), 예외(상태·코드), 스테이지 진행과의 흐름.
 * 실패하면 StagePlayService의 판단 순서, V5 stage_play의 유일 제약(uk_stage_play_one_in_progress), StagePlay 상태 함수,
 * StageResultService의 상태 검사·endWithResult 자리를 의심한다.
 * M24 웨이브 구성(stage-play-api.md v1.3 "웨이브 구성")은 StagePlay21~에서 본다: 시작 때 고정, 모든 응답이 같은 구성,
 * V9 전 플레이 채우기, V9 제약.
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
        assertThat(count("SELECT COUNT(*) FROM stage_play_result WHERE account_id = ?", accountId)).isZero();
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
        assertThat(count("SELECT COUNT(*) FROM stage_play_result WHERE stage_play_id = ?", abandoned)).isZero();
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

    // 확인(M24 웨이브 구성): P1이 몬스터 값을 풀어 둔 구성·버전을 주고, 같은 값을 플레이에 저장한다(wave_count와 같은 길이).
    // 실패하면 StageCatalog.toWaves(풀기), StagePlay.start, V9 칼럼, WavePlanCodec을 의심한다
    @Test
    void 시작하면_웨이브_구성과_버전을_주고_저장한다() throws Exception {
        String token = signupAndLogin("StagePlay21", "플레이21");
        ResultActions started = start(token, newId(), STAGE_1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.waveVersion").value("w2-m1"))
                .andExpect(jsonPath("$.data.waves.length()").value(5))
                .andExpect(jsonPath("$.data.waves[0].waveNumber").value(1))
                .andExpect(jsonPath("$.data.waves[0].restSeconds").value(0.0))
                .andExpect(jsonPath("$.data.waves[0].goldChanceMultiplier").value(1.0))
                .andExpect(jsonPath("$.data.waves[0].enemies[0].monsterId").value("monster.samurai"))
                .andExpect(jsonPath("$.data.waves[0].enemies[0].count").value(2))
                .andExpect(jsonPath("$.data.waves[0].enemies[0].goldReward").value(3))
                .andExpect(jsonPath("$.data.waves[0].enemies[0].goldDropChance").value(1.0))
                .andExpect(jsonPath("$.data.waves[1].enemies[0].goldReward").value(2))
                .andExpect(jsonPath("$.data.waves[4].bossWave").value(true))
                .andExpect(jsonPath("$.data.waves[4].enemies[0].boss").value(true))
                // 없는 스폰 그룹도 칸은 있다(null)
                .andExpect(content().string(containsString("\"spawnGroup\":null")));

        String id = idOf(started);
        assertThat(jdbc.queryForObject("SELECT wave_version FROM stage_play WHERE stage_play_id = ?", String.class, id))
                .isEqualTo("w2-m1");
        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(wave_plan) FROM stage_play WHERE stage_play_id = ?",
                Integer.class, id)).isEqualTo(5);
    }

    // 확인(M24, 중복 방지·이어하기): 같은 플레이는 P1 재전송·P2·P3·P4 모두 같은 구성·버전을 준다.
    // 실패하면 StagePlayService.toResponse를 거치지 않는 길이 있는지 의심한다
    @Test
    void 같은_플레이는_어느_응답이든_같은_구성() throws Exception {
        String token = signupAndLogin("StagePlay22", "플레이22");
        String requestId = newId();
        String firstBody = body(start(token, requestId, STAGE_1).andExpect(status().isCreated()));
        String id = JsonPath.read(firstBody, "$.data.stagePlayId");
        Object waves = JsonPath.read(firstBody, "$.data.waves");

        List<String> bodies = List.of(
                body(start(token, requestId, STAGE_1).andExpect(status().isCreated())),
                body(current(token).andExpect(status().isOk())),
                body(mockMvc.perform(get(PLAYS + "/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())),
                body(abandon(token, id).andExpect(status().isOk())));
        for (String other : bodies) {
            assertThat((Object) JsonPath.read(other, "$.data.waves")).isEqualTo(waves);
            assertThat((String) JsonPath.read(other, "$.data.waveVersion")).isEqualTo("w2-m1");
        }
    }

    // 확인(M24, 플레이에 고정): 응답은 지금 서버 정의가 아니라 플레이에 저장한 구성·버전이다.
    // 서버 정의가 바뀐 것을 저장값을 바꿔 흉내 낸다. 실패하면 toResponse가 StageCatalog를 먼저 보는지 의심한다
    @Test
    void 저장한_구성을_돌려준다() throws Exception {
        String token = signupAndLogin("StagePlay23", "플레이23");
        String id = idOf(start(token, newId(), STAGE_1).andExpect(status().isCreated()));
        jdbc.update("""
                UPDATE stage_play SET wave_plan = JSON_SET(wave_plan, '$[0].enemies[0].count', 99), wave_version = 'w9-m9'
                WHERE stage_play_id = ?
                """, id);

        current(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waveVersion").value("w9-m9"))
                .andExpect(jsonPath("$.data.waves[0].enemies[0].count").value(99));
    }

    // 확인(M24, V9 전 플레이): 구성이 없는 진행 중 플레이는 지금 정의로 채운다. 지금 웨이브 수가 플레이의 waveCount와
    // 다르면(체크포인트 범위와 어긋남) waves·waveVersion을 null로 준다. 실패하면 toResponse의 대체 길을 의심한다
    @Test
    void 구성_저장_전_플레이는_지금_정의로_채운다() throws Exception {
        String token = signupAndLogin("StagePlay24", "플레이24");
        insertInProgress(accountId("StagePlay24"), 5);
        current(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waveVersion").value("w2-m1"))
                .andExpect(jsonPath("$.data.waves.length()").value(5));

        String otherToken = signupAndLogin("StagePlay25", "플레이25");
        insertInProgress(accountId("StagePlay25"), 3);
        current(otherToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waveCount").value(3))
                .andExpect(content().string(containsString("\"waves\":null")))
                .andExpect(content().string(containsString("\"waveVersion\":null")));
    }

    // 확인(M24 DB 제약, V9): 구성과 버전은 함께 있거나 함께 없고, 구성은 JSON 배열이다.
    // CHECK 위반(3819)은 JdbcTemplate에서 UncategorizedSQLException으로 오므로 부모 DataAccessException과 제약 이름으로 본다
    @Test
    void 웨이브_구성_칼럼은_DB가_지킨다() throws Exception {
        signupAndLogin("StagePlay26", "플레이26");
        long accountId = accountId("StagePlay26");
        assertThatThrownBy(() -> insertWithPlan(accountId, "[]", null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_stage_play_wave_plan_pair");
        assertThatThrownBy(() -> insertWithPlan(accountId, "{}", "w1-m1"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_stage_play_wave_plan_array");
        insertWithPlan(accountId, "[]", "w1-m1");
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

    /** 결과 제출(결과 API: POST /accounts/me/stage-plays/{id}/result) */
    private ResultActions submitResult(String token, String stagePlayId, boolean cleared) throws Exception {
        return mockMvc.perform(post(PLAYS + "/{id}/result", stagePlayId).header(HttpHeaders.AUTHORIZATION, bearer(token))
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

    /** V9 전에 시작한 플레이처럼 구성 없이 넣는다(wave_plan·wave_version NULL) */
    private void insertInProgress(long accountId, int waveCount) {
        jdbc.update("""
                INSERT INTO stage_play (stage_play_id, account_id, stage_id, wave_count, difficulty, status,
                                        start_request_id, started_at, expires_at, created_at, updated_at)
                VALUES (?, ?, 'stage.01.01', ?, 0, 'IN_PROGRESS', ?, NOW(3), DATE_ADD(NOW(3), INTERVAL 1 DAY), NOW(3), NOW(3))
                """, newId(), accountId, waveCount, newId());
    }

    /** 끝난 플레이(진행 중 제약에 걸리지 않게 FAILED)로 구성 칼럼만 바꿔 넣는다 */
    private void insertWithPlan(long accountId, String wavePlan, String waveVersion) {
        jdbc.update("""
                INSERT INTO stage_play (stage_play_id, account_id, stage_id, wave_count, wave_plan, wave_version, difficulty,
                                        status, end_reason, start_request_id, started_at, expires_at, ended_at,
                                        created_at, updated_at)
                VALUES (?, ?, 'stage.01.01', 5, ?, ?, 0, 'FAILED', 'ABANDONED', ?, NOW(3), DATE_ADD(NOW(3), INTERVAL 1 DAY),
                        NOW(3), NOW(3), NOW(3))
                """, newId(), accountId, wavePlan, waveVersion, newId());
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
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
