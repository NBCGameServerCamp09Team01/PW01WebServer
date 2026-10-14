package com.pw01.webserver.stageplay;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 플레이 체크포인트 C1 저장·C2 받기(루트 docs/contracts/stage-play-api.md "C1·C2. 체크포인트")를 앱 전체 + 실제 MySQL·Redis로 확인한다.
 * 필수 시험: 판정(웨이브 범위·순서, state 모양·크기), 중복(같은 웨이브 재전송은 덮어씀), 예외(상태·코드), 판이 끝나면 지움.
 * 실패하면 PlayCheckpointService의 검사 순서, play_checkpoint_put.lua, PlayCheckpointCleaner(커밋 뒤 삭제)와
 * StagePlayService.abandon·StageResultService의 StagePlayEndedEvent 발행 자리를 의심한다.
 * 테스트끼리 DB·Redis를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(Checkpoint01~).
 */
@IntegrationTest
class PlayCheckpointApiTest {

    private static final String PASSWORD = "checkpointpass1";
    private static final String STAGE_1 = "stage.01.01";
    private static final String PLAYS = "/accounts/me/stage-plays";
    private static final String KEY_PREFIX = "pw01:play-checkpoint:";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    // 확인: 올린 state가 그대로 돌아온다, 응답 칸(clearedWave·contentVersion·elapsedSeconds·savedAt), Redis 만료 = 플레이 마감 안쪽
    @Test
    void 저장하면_그대로_받는다() throws Exception {
        String token = signupAndLogin("Checkpoint01", "체크포인트01");
        String play = startPlayOk(token);

        save(token, play, body(1, 2, 96.4, """
                {"gold": 295, "items": [{"itemPath": "/Game/Item/DA_Potion.DA_Potion", "quantity": 3}], "attributes": {"currentHealth": 84}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stagePlayId").value(play))
                .andExpect(jsonPath("$.data.clearedWave").value(2))
                .andExpect(jsonPath("$.data.contentVersion").value(1))
                .andExpect(jsonPath("$.data.elapsedSeconds").value(96.4))
                .andExpect(jsonPath("$.data.savedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.state").doesNotExist());

        load(token, play)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clearedWave").value(2))
                .andExpect(jsonPath("$.data.state.gold").value(295))
                .andExpect(jsonPath("$.data.state.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.state.attributes.currentHealth").value(84));

        Long ttl = redis.getExpire(KEY_PREFIX + play);
        assertThat(ttl).isBetween(1L, 24L * 60 * 60);
    }

    // 확인(중복): 같은 웨이브를 다시 보내면 덮어쓴다(200), 다음 웨이브도 덮어쓴다
    @Test
    void 같은_웨이브는_덮어쓰고_다음_웨이브로_올라간다() throws Exception {
        String token = signupAndLogin("Checkpoint02", "체크포인트02");
        String play = startPlayOk(token);

        save(token, play, body(1, 1, 30.0, "{\"gold\": 10}")).andExpect(status().isOk());
        save(token, play, body(1, 1, 31.0, "{\"gold\": 11}")).andExpect(status().isOk());
        load(token, play).andExpect(jsonPath("$.data.state.gold").value(11));

        save(token, play, body(1, 3, 90.0, "{\"gold\": 30}")).andExpect(status().isOk());
        load(token, play)
                .andExpect(jsonPath("$.data.clearedWave").value(3))
                .andExpect(jsonPath("$.data.state.gold").value(30));
    }

    // 확인(판정): 저장된 것보다 앞선 웨이브는 409 CHECKPOINT_OUT_OF_ORDER(retryable false), 저장된 값은 그대로
    @Test
    void 앞선_웨이브는_409() throws Exception {
        String token = signupAndLogin("Checkpoint03", "체크포인트03");
        String play = startPlayOk(token);

        save(token, play, body(1, 3, 90.0, "{\"gold\": 30}")).andExpect(status().isOk());
        save(token, play, body(1, 2, 60.0, "{\"gold\": 20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKPOINT_OUT_OF_ORDER"))
                .andExpect(jsonPath("$.retryable").value(false));

        load(token, play)
                .andExpect(jsonPath("$.data.clearedWave").value(3))
                .andExpect(jsonPath("$.data.state.gold").value(30));
    }

    // 확인(판정): clearedWave 0·waveCount 이상, contentVersion 0, elapsedSeconds 음수, state가 객체가 아님·없음·너무 큼 → 400 VALIDATION_FAILED
    @Test
    void 이상한_값은_400() throws Exception {
        String token = signupAndLogin("Checkpoint04", "체크포인트04");
        String play = startPlayOk(token);

        expectValidation(save(token, play, body(1, 0, 1.0, "{}")), "clearedWave");
        expectValidation(save(token, play, body(1, 5, 1.0, "{}")), "clearedWave");
        expectValidation(save(token, play, body(0, 1, 1.0, "{}")), "contentVersion");
        expectValidation(save(token, play, body(1, 1, -1.0, "{}")), "elapsedSeconds");
        expectValidation(save(token, play, body(1, 1, 1.0, "[1, 2]")), "state");
        expectValidation(save(token, play, body(1, 1, 1.0, "\"text\"")), "state");
        expectValidation(save(token, play, """
                {"contentVersion": 1, "clearedWave": 1, "elapsedSeconds": 1.0}
                """), "state");
        expectValidation(save(token, play, body(1, 1, 1.0, "{\"blob\": \"" + "x".repeat(70 * 1024) + "\"}")), "state");

        load(token, play).andExpect(status().isNoContent());
    }

    // 확인(예외): 남의 플레이·없는 플레이 404, UUID가 아닌 ID 400, 올린 적 없으면 204
    @Test
    void 플레이_규칙() throws Exception {
        String owner = signupAndLogin("Checkpoint05", "체크포인트05");
        String other = signupAndLogin("Checkpoint06", "체크포인트06");
        String play = startPlayOk(owner);

        load(owner, play).andExpect(status().isNoContent());

        save(other, play, body(1, 1, 1.0, "{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_FOUND"));
        load(other, play).andExpect(status().isNotFound());
        save(owner, newId(), body(1, 1, 1.0, "{}")).andExpect(status().isNotFound());
        load(owner, "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("stagePlayId"));
    }

    // 확인(예외): 마감이 지난 플레이 409 STAGE_PLAY_EXPIRED·받기 204, 포기한 플레이 409 STAGE_PLAY_NOT_IN_PROGRESS
    @Test
    void 끝났거나_마감이_지난_플레이는_409() throws Exception {
        String token = signupAndLogin("Checkpoint07", "체크포인트07");
        String expired = startPlayOk(token);
        save(token, expired, body(1, 1, 1.0, "{}")).andExpect(status().isOk());
        jdbc.update("UPDATE stage_play SET expires_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE stage_play_id = ?", expired);

        save(token, expired, body(1, 2, 2.0, "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_EXPIRED"))
                .andExpect(jsonPath("$.retryable").value(false));
        // 마감이 지나면 Redis에 값이 남아 있어도 내보내지 않는다(실제로는 마감 때 만료로 사라짐)
        load(token, expired).andExpect(status().isNoContent());

        mockMvc.perform(get(PLAYS + "/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent()); // 여기서 EXPIRED로 끝나 새로 시작할 수 있다
        String abandoned = startPlayOk(token);
        abandon(token, abandoned).andExpect(status().isOk());
        save(token, abandoned, body(1, 1, 1.0, "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_PLAY_NOT_IN_PROGRESS"));
    }

    // 확인(판이 끝나면 지움): 포기·결과 저장 뒤 Redis 키가 없고 받기는 204
    @Test
    void 포기하거나_결과를_내면_지운다() throws Exception {
        String token = signupAndLogin("Checkpoint08", "체크포인트08");

        String abandoned = startPlayOk(token);
        save(token, abandoned, body(1, 2, 60.0, "{\"gold\": 1}")).andExpect(status().isOk());
        assertThat(redis.hasKey(KEY_PREFIX + abandoned)).isTrue();
        abandon(token, abandoned).andExpect(status().isOk());
        assertThat(redis.hasKey(KEY_PREFIX + abandoned)).isFalse();
        load(token, abandoned).andExpect(status().isNoContent());

        String finished = startPlayOk(token);
        save(token, finished, body(1, 2, 60.0, "{\"gold\": 2}")).andExpect(status().isOk());
        mockMvc.perform(post(PLAYS + "/{id}/result", finished).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "stageId": "%s", "cleared": false, "reachedWave": 3,
                                 "totalWaveCount": 5, "playTimeSeconds": 1.5, "earnedGold": 10, "killCount": 1}
                                """.formatted(newId(), STAGE_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("SAVED"));
        assertThat(redis.hasKey(KEY_PREFIX + finished)).isFalse();
        load(token, finished).andExpect(status().isNoContent());
    }

    private void expectValidation(ResultActions actions, String field) throws Exception {
        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    private static String body(int contentVersion, int clearedWave, double elapsedSeconds, String stateJson) {
        return """
                {"contentVersion": %d, "clearedWave": %d, "elapsedSeconds": %s, "state": %s}
                """.formatted(contentVersion, clearedWave, elapsedSeconds, stateJson);
    }

    private ResultActions save(String token, String stagePlayId, String body) throws Exception {
        return mockMvc.perform(put(PLAYS + "/{id}/checkpoint", stagePlayId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions load(String token, String stagePlayId) throws Exception {
        return mockMvc.perform(get(PLAYS + "/{id}/checkpoint", stagePlayId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions abandon(String token, String stagePlayId) throws Exception {
        return mockMvc.perform(post(PLAYS + "/{id}/abandon", stagePlayId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String startPlayOk(String token) throws Exception {
        String body = mockMvc.perform(post(PLAYS).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "stageId": "%s"}
                                """.formatted(newId(), STAGE_1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.stagePlayId");
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
