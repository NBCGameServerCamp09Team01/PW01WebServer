package com.pw01.webserver.stage;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.stage.service.StageProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ST1 GET /stages(공개)와 ST2 GET /accounts/me/stages(내 진행)를 앱 전체 + 실제 MySQL·Redis(테스트 컨테이너)로 확인한다.
 * 클리어는 S2 결과 API가 아직 없어서 서비스(recordResult)를 트랜잭션 안에서 직접 부른다. S2 병합 뒤 흐름 시험은 결과 API로 한다.
 * 응답 모양은 루트 docs/contracts/stage-api.md 예시(examples/stage-list-response.json, stage-progress-response.json)와 같아야 한다.
 * 실패하면 WebConfig 인증 경로, 두 컨트롤러, stages.json 값을 의심한다.
 * 테스트끼리 DB·Redis를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(StageApi01~).
 */
@IntegrationTest
class StageApiTest {

    private static final String PASSWORD = "stagepass01";
    private static final String PLAY_1 = "3f2b8c1e-7a4d-4e2f-9b10-6c5d4e3f2a1b";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StageProgressService stageProgressService;

    @Autowired
    TransactionTemplate tx;

    // 확인: ST1은 토큰 없이 명세 예시와 같은 정의를 준다(계정 진행 칸 없음)
    @Test
    void 정의는_토큰_없이() throws Exception {
        mockMvc.perform(get("/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.regions[0].regionId").value("region.01"))
                .andExpect(jsonPath("$.data.regions[0].name").value("지역 1"))
                .andExpect(jsonPath("$.data.regions[0].order").value(1))
                .andExpect(jsonPath("$.data.regions[0].stages[0].stageId").value("stage.01.01"))
                .andExpect(jsonPath("$.data.regions[0].stages[0].name").value("스테이지 1"))
                .andExpect(jsonPath("$.data.regions[0].stages[0].order").value(1))
                .andExpect(jsonPath("$.data.regions[0].stages[0].waveCount").value(5))
                .andExpect(jsonPath("$.data.regions[0].stages[1].stageId").value("stage.01.02"))
                .andExpect(jsonPath("$.data.regions[0].stages[1].requires").value("stage.01.01"))
                .andExpect(jsonPath("$.data.regions[0].stages[0].status").doesNotExist())
                // 없는 값도 칸은 있다(null). jsonPath의 isEmpty는 칸이 없어도 통과하므로 본문으로 본다
                .andExpect(content().string(containsString("\"requires\":null")));
    }

    // 확인: 새 계정의 ST2 — 스테이지 1 OPEN, 2 LOCKED, 클리어 0, 처음 클리어 시각 null(칸은 있음)
    @Test
    void 새_계정_진행() throws Exception {
        String token = signupAndLogin("StageApi01", "스테이지API01");

        mockMvc.perform(get("/accounts/me/stages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clearedCount").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.stages[0].stageId").value("stage.01.01"))
                .andExpect(jsonPath("$.data.stages[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.stages[1].stageId").value("stage.01.02"))
                .andExpect(jsonPath("$.data.stages[1].status").value("LOCKED"))
                .andExpect(content().string(containsString("\"firstClearedAt\":null")));
    }

    // 확인: 스테이지 1 클리어 뒤 ST2 — 1 CLEARED(시각 있음)·2 OPEN, 클리어 1
    @Test
    void 클리어_뒤_진행() throws Exception {
        String token = signupAndLogin("StageApi02", "스테이지API02");
        Long accountId = jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = 'StageApi02'", Long.class);
        tx.executeWithoutResult(status -> stageProgressService.recordResult(accountId, "stage.01.01", true, PLAY_1));

        mockMvc.perform(get("/accounts/me/stages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clearedCount").value(1))
                .andExpect(jsonPath("$.data.stages[0].status").value("CLEARED"))
                .andExpect(jsonPath("$.data.stages[0].firstClearedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.stages[1].status").value("OPEN"));
    }

    // 확인: ST2는 토큰 없이 부르면 401(/accounts/** 인증 경로)
    @Test
    void 내_진행은_토큰_없이_401() throws Exception {
        mockMvc.perform(get("/accounts/me/stages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    // SF-9(AuthTestSupport) 병합 뒤 그 도우미로 바꾼다
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
