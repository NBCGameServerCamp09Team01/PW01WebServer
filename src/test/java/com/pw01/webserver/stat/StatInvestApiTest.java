package com.pw01.webserver.stat;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 묶음 스탯 투자를 앱 전체 + 실제 MySQL·Redis로 확인한다(루트 docs/contracts/stat-api.md).
 * 필수 시험: 서버 판정(포인트·최대치·0 아래, 전부 성공 또는 전부 실패), 중복 방지(같은 requestId 두 번 → 한 번), 예외(상태·코드).
 * 포인트는 jdbc로 넣는다(포인트를 얻는 길은 S2 결과 시험이 맡음).
 * 실패하면 AccountService.changeStats 판정 순서, ProgressWriter 재시도, IdempotencyService 키·해시, V6 마이그레이션을 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(StatInvest01~).
 */
@IntegrationTest
class StatInvestApiTest {

    private static final String PASSWORD = "statinvestpass1";
    private static final String HP = "Account.Stat.MaxHealth";
    private static final String ST = "Account.Stat.MaxStamina";
    private static final String ATK = "Account.Stat.AttackPower";
    private static final String DEF = "Account.Stat.DefensePower";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    // 확인(서버 판정·저장): 묶음 올림 → 포인트 줄고 스탯별 값·내역 줄 수·version, 로그인 조회(/accounts/me)에도 실림.
    //            실패하면 changeStats 저장, getSnapshot의 investedStats 싣기를 의심
    @Test
    void 묶음으로_올리면_포인트가_줄고_계정_값에_실린다() throws Exception {
        String token = signupAndLogin("StatInvest01", "스탯투자01");
        long accountId = accountId("StatInvest01");
        setPoints(accountId, 6);
        long versionBefore = version(accountId);

        invest(token, body(newId(), HP, 2, ATK, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("SAVED"))
                .andExpect(jsonPath("$.data.result.statPoints").value(3))
                .andExpect(jsonPath("$.data.result.investedStats['" + HP + "']").value(2))
                .andExpect(jsonPath("$.data.result.investedStats['" + ATK + "']").value(1))
                .andExpect(jsonPath("$.data.account.statPoints").value(3))
                .andExpect(jsonPath("$.data.account.investedStats['" + HP + "']").value(2));

        assertThat(version(accountId)).isGreaterThan(versionBefore);
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ? AND kind = 'STAT_INVEST'", accountId))
                .isEqualTo(2);
        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.statPoints").value(3))
                .andExpect(jsonPath("$.data.investedStats['" + ATK + "']").value(1))
                .andExpect(jsonPath("$.data.investedStats['" + DEF + "']").doesNotExist());
    }

    // 확인(E1·전부 실패): 합계가 남은 포인트보다 크면 409, 포인트·스탯·내역 그대로
    @Test
    void 포인트가_모자라면_409이고_아무것도_안_바뀐다() throws Exception {
        String token = signupAndLogin("StatInvest02", "스탯투자02");
        long accountId = accountId("StatInvest02");
        setPoints(accountId, 2);

        invest(token, body(newId(), HP, 2, ATK, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAT_POINTS_NOT_ENOUGH"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertNothingChanged(accountId, 2);
    }

    // 확인(E2): 없는 스탯은 400 STAT_UNKNOWN, errors에 칸 이름
    @Test
    void 없는_스탯은_400() throws Exception {
        String token = signupAndLogin("StatInvest03", "스탯투자03");
        long accountId = accountId("StatInvest03");
        setPoints(accountId, 5);

        invest(token, body(newId(), HP, 1, "Account.Stat.Luck", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAT_UNKNOWN"))
                .andExpect(jsonPath("$.errors[0].field").value("investments[1].statTag"));

        assertNothingChanged(accountId, 5);
    }

    // 확인(E4·전부 실패): 묶음 중 하나만 최대치를 넘어도 409이고 다른 스탯도 안 바뀐다
    @Test
    void 최대치를_넘는_스탯이_하나라도_있으면_409() throws Exception {
        String token = signupAndLogin("StatInvest04", "스탯투자04");
        long accountId = accountId("StatInvest04");
        setPoints(accountId, 20);
        invest(token, body(newId(), HP, 9)).andExpect(status().isOk());

        invest(token, body(newId(), ATK, 3, HP, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAT_MAX_EXCEEDED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("investments[1].statTag"));

        assertThat(points(accountId)).isEqualTo(11);
        assertThat(statPoints(accountId, ATK)).isZero();
        assertThat(statPoints(accountId, HP)).isEqualTo(9);
    }

    // 확인(형식): 같은 스탯 두 번·points 0·빈 목록·묶음 상한 초과·모르는 칸은 400
    @Test
    void 형식이_틀리면_400() throws Exception {
        String token = signupAndLogin("StatInvest05", "스탯투자05");
        setPoints(accountId("StatInvest05"), 50);

        invest(token, body(newId(), HP, 1, HP, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("investments[1].statTag"));
        invest(token, body(newId(), HP, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("investments[0].points"));
        invest(token, """
                {"requestId": "%s", "investments": []}
                """.formatted(newId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            many.append(i == 0 ? "" : ",").append("{\"statTag\": \"Account.Stat.T").append(i).append("\", \"points\": 1}");
        }
        invest(token, "{\"requestId\": \"" + newId() + "\", \"investments\": [" + many + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("investments"));
        invest(token, """
                {"requestId": "%s", "investments": [{"statTag": "%s", "points": 1}], "extra": 1}
                """.formatted(newId(), HP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    // 확인(내림): 투자한 스탯을 내리면 포인트를 돌려받고 내역은 STAT_REFUND. 0이 된 스탯은 계정 값에서 빠진다.
    //            내역 합(point_delta)은 포인트 변화와 같다
    @Test
    void 내리면_포인트를_돌려받는다() throws Exception {
        String token = signupAndLogin("StatInvest06", "스탯투자06");
        long accountId = accountId("StatInvest06");
        setPoints(accountId, 4);
        invest(token, body(newId(), DEF, 3)).andExpect(status().isOk());

        invest(token, body(newId(), DEF, -3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.statPoints").value(4))
                .andExpect(jsonPath("$.data.result.investedStats['" + DEF + "']").doesNotExist());

        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ? AND kind = 'STAT_REFUND'", accountId))
                .isEqualTo(1);
        assertThat(count("SELECT COALESCE(SUM(point_delta), 0) FROM account_ledger WHERE account_id = ?", accountId))
                .isZero();
    }

    // 확인: 투자한 것보다 많이 내리면 409 STAT_BELOW_ZERO, 안 바뀜
    @Test
    void 투자한_것보다_많이_내리면_409() throws Exception {
        String token = signupAndLogin("StatInvest07", "스탯투자07");
        long accountId = accountId("StatInvest07");
        setPoints(accountId, 3);
        invest(token, body(newId(), ST, 1)).andExpect(status().isOk());

        invest(token, body(newId(), ST, -2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAT_BELOW_ZERO"))
                .andExpect(jsonPath("$.errors[0].field").value("investments[0].statTag"));

        assertThat(points(accountId)).isEqualTo(2);
        assertThat(statPoints(accountId, ST)).isEqualTo(1);
    }

    // 확인(섞은 묶음): 남은 포인트 0에서 A −1, B +1 → 성공(돌려받은 포인트로 올림). 포인트가 그대로여도 version은 오른다
    @Test
    void 내려서_받은_포인트로_다른_스탯을_올린다() throws Exception {
        String token = signupAndLogin("StatInvest08", "스탯투자08");
        long accountId = accountId("StatInvest08");
        setPoints(accountId, 1);
        invest(token, body(newId(), ATK, 1)).andExpect(status().isOk());
        long versionBefore = version(accountId);

        invest(token, body(newId(), ATK, -1, DEF, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.statPoints").value(0))
                .andExpect(jsonPath("$.data.result.investedStats['" + DEF + "']").value(1))
                .andExpect(jsonPath("$.data.result.investedStats['" + ATK + "']").doesNotExist());

        assertThat(version(accountId)).isGreaterThan(versionBefore);
    }

    // 확인(E5·SF-5): 같은 requestId·같은 본문을 두 번 → 두 번째도 처음 응답(SAVED) 그대로, 포인트는 한 번만.
    //            실패하면 IdempotencyService의 처음 응답 기억(SET XX)을 의심
    @Test
    void 같은_요청을_두_번_보내도_한_번만_반영() throws Exception {
        String token = signupAndLogin("StatInvest09", "스탯투자09");
        long accountId = accountId("StatInvest09");
        setPoints(accountId, 5);
        String body = body(newId(), HP, 2);

        String first = invest(token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = invest(token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((Object) JsonPath.read(second, "$.data")).isEqualTo(JsonPath.read(first, "$.data"));
        assertThat(points(accountId)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isEqualTo(1);
    }

    // 확인(SF-5): 같은 requestId에 다른 본문은 409 IDEMPOTENCY_KEY_REUSED(retryable false)
    @Test
    void 같은_requestId에_다른_본문은_409() throws Exception {
        String token = signupAndLogin("StatInvest10", "스탯투자10");
        setPoints(accountId("StatInvest10"), 5);
        String requestId = newId();
        invest(token, body(requestId, HP, 1)).andExpect(status().isOk());

        invest(token, body(requestId, HP, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    // 확인(DB 방어): Redis 기억이 사라진 뒤 같은 요청이 와도 변경 내역으로 알아채 ALREADY_SAVED, 포인트는 한 번만
    @Test
    void 기억이_사라진_뒤에도_두_번_반영하지_않는다() throws Exception {
        String token = signupAndLogin("StatInvest11", "스탯투자11");
        long accountId = accountId("StatInvest11");
        setPoints(accountId, 5);
        String requestId = newId();
        invest(token, body(requestId, HP, 2)).andExpect(status().isOk());
        redis.delete("pw01:idem:" + accountId + ":" + requestId);

        invest(token, body(requestId, HP, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("ALREADY_SAVED"))
                .andExpect(jsonPath("$.data.result.statPoints").value(3));

        assertThat(points(accountId)).isEqualTo(3);
    }

    // 확인(4xx는 기억하지 않음): 포인트 부족으로 거절된 요청을 포인트가 생긴 뒤 같은 requestId로 다시 보내면 성공
    @Test
    void 거절된_요청은_다시_판정한다() throws Exception {
        String token = signupAndLogin("StatInvest12", "스탯투자12");
        long accountId = accountId("StatInvest12");
        setPoints(accountId, 0);
        String body = body(newId(), HP, 1);
        invest(token, body).andExpect(status().isConflict());

        setPoints(accountId, 1);
        invest(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("SAVED"));
    }

    // 확인(동시): 포인트가 하나뿐인데 다른 요청 둘이 동시에 오면 하나만 반영(다른 하나는 409). 포인트는 0 아래로 안 감.
    //            실패하면 진행 행 @Version 충돌 → ProgressWriter 새 트랜잭션 재시도를 의심
    @Test
    void 동시에_와도_포인트는_한_번만_쓴다() throws Exception {
        String token = signupAndLogin("StatInvest13", "스탯투자13");
        long accountId = accountId("StatInvest13");
        setPoints(accountId, 1);

        List<Integer> statuses = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> invest(token, body(newId(), HP, 1)).andReturn().getResponse().getStatus(),
                    () -> invest(token, body(newId(), ATK, 1)).andReturn().getResponse().getStatus());
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(points(accountId)).isZero();
        assertThat(count("SELECT COALESCE(SUM(points), 0) FROM account_stat WHERE account_id = ?", accountId))
                .isEqualTo(1);
    }

    // 확인: 토큰 없으면 401
    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/accounts/me/stat-investments").contentType(MediaType.APPLICATION_JSON)
                        .content(body(newId(), HP, 1)))
                .andExpect(status().isUnauthorized());
    }

    private void assertNothingChanged(long accountId, int expectedPoints) {
        assertThat(points(accountId)).isEqualTo(expectedPoints);
        assertThat(count("SELECT COUNT(*) FROM account_stat WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isZero();
    }

    private ResultActions invest(String token, String body) throws Exception {
        return mockMvc.perform(post("/accounts/me/stat-investments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 스탯·포인트 쌍을 이어서 받는다: body(id, A, 2, B, -1) */
    private static String body(String requestId, Object... pairs) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            items.append(i == 0 ? "" : ",")
                    .append("{\"statTag\": \"").append(pairs[i]).append("\", \"points\": ").append(pairs[i + 1]).append("}");
        }
        return "{\"requestId\": \"" + requestId + "\", \"investments\": [" + items + "]}";
    }

    private void setPoints(long accountId, int points) {
        jdbc.update("UPDATE account_progress SET stat_points = ? WHERE account_id = ?", points, accountId);
    }

    private long points(long accountId) {
        return count("SELECT stat_points FROM account_progress WHERE account_id = ?", accountId);
    }

    private long version(long accountId) {
        return count("SELECT version FROM account_progress WHERE account_id = ?", accountId);
    }

    private long statPoints(long accountId, String statKey) {
        Long value = jdbc.query("SELECT points FROM account_stat WHERE account_id = ? AND stat_key = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, accountId, statKey);
        return value == null ? 0 : value;
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
