package com.pw01.webserver.account;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 스냅샷(M25)을 앱 전체 + 실제 MySQL·Redis로 확인한다(루트 docs/contracts/account-api.md "싣는 응답"·"버전 규칙").
 * 계정 값을 싣는 응답 다섯(로그인·A3·결과·투자·해금)이 같은 조립(AccountService.snapshot)에서 나오는지,
 * 즉 같은 상태에서 A3와 통째로 같고 바꾸는 요청마다 version이 오르는지 본다. 게임은 이 version으로 늦은 응답을 버린다.
 * 실패하면 그 응답이 snapshot()이 아닌 길로 account를 만들었는지, 결과 SAVED 응답이 저장 트랜잭션 안에서
 * 커밋 전 version으로 만들어졌는지(AccountService.grantStageReward)를 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 다른 시험과 겹치지 않게 쓴다(Snapshot01~).
 */
@IntegrationTest
class AccountSnapshotConsistencyTest {

    private static final String PASSWORD = "snapshotpass1";
    private static final String STAGE_1 = "stage.01.01";
    private static final String HEALTH = "Account.Stat.MaxHealth";
    private static final String COMBO4 = "Account.Skill.Combo4";

    @Autowired
    MockMvc mockMvc;

    // 확인(끝 기준 1): 로그인 → 결과 → 투자 → 해금 → 다시 로그인 → 같은 결과 다시(ALREADY_SAVED) 순서로,
    //            응답마다 account가 바로 뒤 A3(GET /accounts/me)와 통째로 같고 바꾸는 요청마다 version이 오른다.
    //            실패하면 위 클래스 주석의 두 곳을 의심
    @Test
    void 응답_다섯의_account는_같은_조립에서_나온다() throws Exception {
        signup("Snapshot01", "스냅샷일치01");

        // 로그인: 처음 값
        String login = login("Snapshot01");
        String token = JsonPath.read(login, "$.data.accessToken");
        Map<String, Object> afterLogin = JsonPath.read(login, "$.data.account");
        assertThat(afterLogin).isEqualTo(me(token));

        // 결과(클리어): 보상이 들어가고 version이 오른다
        String stagePlayId = startPlayOk(token);
        String resultBody = resultBody(newId());
        Map<String, Object> afterResult = account(submit(token, stagePlayId, resultBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("SAVED")));
        assertThat(afterResult).isEqualTo(me(token));
        assertThat(version(afterResult)).isGreaterThan(version(afterLogin));

        // 투자: 체력 +1
        Map<String, Object> afterInvest = account(invest(token, HEALTH, 1)
                .andExpect(status().isOk()));
        assertThat(afterInvest).isEqualTo(me(token));
        assertThat(version(afterInvest)).isGreaterThan(version(afterResult));

        // 해금: Combo4(비용 1, 클리어 1번)
        Map<String, Object> afterUnlock = account(unlock(token, COMBO4)
                .andExpect(status().isOk()));
        assertThat(afterUnlock).isEqualTo(me(token));
        assertThat(version(afterUnlock)).isGreaterThan(version(afterInvest));

        // 다시 로그인: 계정당 세션 하나라 새 토큰. 로그인 응답의 account도 같은 값
        String relogin = login("Snapshot01");
        String newToken = JsonPath.read(relogin, "$.data.accessToken");
        Map<String, Object> afterRelogin = JsonPath.read(relogin, "$.data.account");
        assertThat(afterRelogin).isEqualTo(afterUnlock);
        assertThat(me(newToken)).isEqualTo(afterUnlock);

        // 같은 결과 다시: ALREADY_SAVED의 account는 처음(결과 직후) 값이 아니라 지금 값(result-api.md v1.2)
        Map<String, Object> replay = account(submit(newToken, stagePlayId, resultBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("ALREADY_SAVED")));
        assertThat(replay).isEqualTo(afterUnlock);
    }

    private Map<String, Object> me(String token) throws Exception {
        String body = mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    private static Map<String, Object> account(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.data.account");
    }

    private static long version(Map<String, Object> account) {
        return ((Number) account.get("version")).longValue();
    }

    private String startPlayOk(String token) throws Exception {
        String body = mockMvc.perform(post("/accounts/me/stage-plays").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "stageId": "%s"}
                                """.formatted(newId(), STAGE_1)))
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

    /** 클리어. 플레이 시간은 짧게(시작 직후라도 경과 + 여유 60초 안) */
    private static String resultBody(String requestId) {
        return """
                {"requestId": "%s", "stageId": "%s", "difficulty": 0, "cleared": true, "reachedWave": 5,
                 "totalWaveCount": 5, "playTimeSeconds": 1.5, "earnedGold": 120, "killCount": 4}
                """.formatted(requestId, STAGE_1);
    }

    private ResultActions invest(String token, String statTag, int points) throws Exception {
        return mockMvc.perform(post("/accounts/me/stat-investments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "investments": [{"statTag": "%s", "points": %d}]}
                        """.formatted(newId(), statTag, points)));
    }

    private ResultActions unlock(String token, String skillTag) throws Exception {
        return mockMvc.perform(post("/accounts/me/skill-unlocks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "skillTag": "%s"}
                        """.formatted(newId(), skillTag)));
    }

    private void signup(String loginId, String nickname) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s", "nickname": "%s"}
                                """.formatted(loginId, PASSWORD, nickname)))
                .andExpect(status().isCreated());
    }

    private String login(String loginId) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(loginId, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

}
