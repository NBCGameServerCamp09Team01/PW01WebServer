package com.pw01.webserver.auth;

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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 첫 흐름 전체(가입 → 로그인 → 메인화면 값 → 접속 점검 → 로그아웃)를 앱 전체 + 실제 MySQL·Redis(테스트 컨테이너)로 확인한다.
 * 인터셉터·전역 예외 처리·Redis 저장소·V2가 함께 맞물리는지 본다. 판단 순서의 세부는 AuthService 단위 시험이 본다.
 * 실패하면 RedisSessionStore·RedisLoginFailStore, WebConfig 경로 목록, GlobalExceptionHandler, AuthService 순서를 의심한다.
 * 테스트끼리 DB·Redis를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(Flow01~).
 */
@IntegrationTest
class AuthFlowApiTest {

    private static final String PASSWORD = "flowpass01";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    /** 한 바퀴: 로그인 200(토큰·메인화면 값) → /accounts/me 200 → 점검 200(만료 시각) → 로그아웃 204 → 같은 토큰 401 AUTH_SESSION_NOT_FOUND */
    @Test
    void fullFlow() throws Exception {
        signup("Flow01", "흐름01");

        String body = login("Flow01", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.account.accountLevel").value(1))
                .andExpect(jsonPath("$.account.experience").value(0))
                .andExpect(jsonPath("$.account.statPoints").value(0))
                .andExpect(jsonPath("$.account.version").value(0))
                .andExpect(jsonPath("$.account.investedStats").isMap())
                .andExpect(jsonPath("$.account.unlockedSkills").isArray())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String loginExpiresAt = JsonPath.read(body, "$.sessionExpiresAt");
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(redis.keys("pw01:*" + token + "*")).isEmpty();

        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountLevel").value(1));

        String heartbeat = mockMvc.perform(post("/auth/heartbeat").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant extended = Instant.parse(JsonPath.read(heartbeat, "$.sessionExpiresAt"));
        assertThat(extended).isAfterOrEqualTo(Instant.parse(loginExpiresAt));

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/accounts/me"));
    }

    /** 다른 곳에서 로그인: 이전 토큰은 401 AUTH_SESSION_REPLACED, 새 토큰은 200 */
    @Test
    void newLoginReplacesOldToken() throws Exception {
        signup("Flow02", "흐름02");
        String first = token(login("Flow02", PASSWORD));
        String second = token(login("Flow02", PASSWORD));

        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_REPLACED"));
        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(second)))
                .andExpect(status().isOk());
    }

    /** 틀린 비밀번호: 1~4번째 401, 5번째 429(retryAfterSeconds + Retry-After). 잠긴 동안은 맞는 비밀번호도 429 */
    @Test
    void fifthFailureLocks() throws Exception {
        signup("Flow03", "흐름03");
        for (int i = 1; i <= 4; i++) {
            login("Flow03", "wrongpass0" + i)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        }

        login("Flow03", "wrongpass05")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_LOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        login("Flow03", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_LOCKED"));
    }

    /** 제재 계정: 비밀번호가 맞으면 403 ACCOUNT_SUSPENDED, 세션은 만들지 않는다 */
    @Test
    void suspendedAccountIsForbidden() throws Exception {
        signup("Flow04", "흐름04");
        Long accountId = jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = 'Flow04'", Long.class);
        jdbc.update("UPDATE account SET status = 'SUSPENDED' WHERE account_id = ?", accountId);

        login("Flow04", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        assertThat(redis.hasKey("pw01:account-session:" + accountId)).isFalse();
    }

    /** 아이디는 대소문자를 구분한다: Flow05로 가입하면 FLOW05 로그인은 401 */
    @Test
    void loginIdIsCaseSensitive() throws Exception {
        signup("Flow05", "흐름05");

        login("FLOW05", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        login("Flow05", PASSWORD).andExpect(status().isOk());
    }

    /** 형식 밖 아이디는 같은 401이고 Redis 실패 키를 만들지 않는다 */
    @Test
    void malformedLoginIdLeavesNoFailKey() throws Exception {
        login("flow_06", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        assertThat(redis.hasKey("pw01:login-fail:flow_06")).isFalse();
    }

    /** 인증 헤더 없음 401 AUTH_TOKEN_MISSING, 모양이 틀린 토큰 401 AUTH_TOKEN_INVALID(전역 예외 처리로 같은 오류 본문) */
    @Test
    void missingOrMalformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
        mockMvc.perform(post("/auth/heartbeat").header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    private void signup(String loginId, String nickname) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s", "nickname": "%s"}
                                """.formatted(loginId, PASSWORD, nickname)))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId": "%s", "password": "%s"}
                        """.formatted(loginId, password)));
    }

    private static String token(ResultActions login) throws Exception {
        return JsonPath.read(login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

}
