package com.pw01.webserver.skill;

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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스킬 해금(K1)·진행도 조회(K2)를 앱 전체 + 실제 MySQL·Redis로 확인한다(루트 docs/contracts/skill-api.md).
 * 필수 시험: 서버 판정(선행·포인트·누적 기록 조건), 중복 방지(같은 requestId 두 번 → 한 번), 예외(상태·코드·requirements).
 * 레벨·포인트·누적 통계는 jdbc로 넣는다(쌓는 길은 마지막 시험과 S2 결과 시험이 맡음).
 * 스킬 값은 master/skills.json(Combo4: 비용 1·클리어 1 / DodgeMastery: 비용 2·Lv3·120초 / CounterPlus: 비용 2·선행 Combo4·처치 10·웨이브 3·900초 이하).
 * 실패하면 AccountService.unlockSkill 판정 순서, SkillJudge 조건 순서, RequirementEvaluatorConfig 표, V7 마이그레이션을 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 아이디·닉네임을 서로 다르게 쓴다(SkillApi01~).
 */
@IntegrationTest
class SkillApiTest {

    private static final String PASSWORD = "skillapipass1";
    private static final String COMBO4 = "Account.Skill.Combo4";
    private static final String DODGE = "Account.Skill.DodgeMastery";
    private static final String COUNTER = "Account.Skill.CounterPlus";
    private static final String STAGE_1 = "stage.01.01";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    // 확인(서버 판정·저장): 조건이 맞으면 포인트가 빠지고 해금 한 줄·내역 한 줄, 응답·로그인 조회에 unlockedSkills.
    //            실패하면 unlockSkill 저장, getSnapshot의 unlockedSkills 싣기를 의심
    @Test
    void 조건을_채우면_해금되고_계정_값에_실린다() throws Exception {
        String token = signupAndLogin("SkillApi01", "스킬해금01");
        long accountId = accountId("SkillApi01");
        setPoints(accountId, 3);
        setTotal(accountId, "stage_play.cleared", 1);
        long versionBefore = version(accountId);

        unlock(token, body(newId(), COMBO4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("SAVED"))
                .andExpect(jsonPath("$.data.result.skillTag").value(COMBO4))
                .andExpect(jsonPath("$.data.result.unlockedSkills", contains(COMBO4)))
                .andExpect(jsonPath("$.data.result.statPoints").value(2))
                .andExpect(jsonPath("$.data.account.unlockedSkills", contains(COMBO4)))
                .andExpect(jsonPath("$.data.account.statPoints").value(2));

        assertThat(version(accountId)).isGreaterThan(versionBefore);
        assertThat(count("SELECT COUNT(*) FROM account_skill WHERE account_id = ?", accountId)).isEqualTo(1);
        assertThat(count("SELECT point_delta FROM account_ledger WHERE account_id = ? AND kind = 'SKILL_UNLOCK'",
                accountId)).isEqualTo(-1);
        mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.unlockedSkills", contains(COMBO4)));
    }

    // 확인(E1·추가 칸): 포인트만 모자라도 409 SKILL_REQUIREMENTS_NOT_MET, 본문 requirements에 조건 전부(충족한 것도), 안 바뀜.
    //            실패하면 ErrorResponse의 기능별 추가 칸(@JsonAnyGetter), SkillErrors.requirementsNotMet을 의심
    @Test
    void 포인트가_모자라면_409와_조건_전부() throws Exception {
        String token = signupAndLogin("SkillApi02", "스킬해금02");
        long accountId = accountId("SkillApi02");
        setPoints(accountId, 0);
        setTotal(accountId, "stage_play.cleared", 1);

        unlock(token, body(newId(), COMBO4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKILL_REQUIREMENTS_NOT_MET"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.requirements", hasSize(2)))
                .andExpect(jsonPath("$.requirements[0].type").value("STAT_POINTS"))
                .andExpect(jsonPath("$.requirements[0].target").value(1.0))
                .andExpect(jsonPath("$.requirements[0].current").value(0.0))
                .andExpect(jsonPath("$.requirements[0].met").value(false))
                .andExpect(jsonPath("$.requirements[1].type").value("STAGES_CLEARED"))
                .andExpect(jsonPath("$.requirements[1].met").value(true))
                .andExpect(jsonPath("$.extra").doesNotExist());

        assertNothingChanged(accountId, 0);
    }

    // 확인(E4): 선행 스킬을 해금하지 않았으면 409, requirements 첫 줄이 PREREQUISITE(key = 선행 스킬) met false
    @Test
    void 선행_스킬이_없으면_409() throws Exception {
        String token = signupAndLogin("SkillApi03", "스킬해금03");
        long accountId = accountId("SkillApi03");
        setPoints(accountId, 5);
        setTotal(accountId, "stage_play.kills", 10);
        setTotal(accountId, "stage_play.max_wave", 3);
        setTotal(accountId, "stage_play.best_clear_ms", 900_000);

        unlock(token, body(newId(), COUNTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKILL_REQUIREMENTS_NOT_MET"))
                .andExpect(jsonPath("$.requirements", hasSize(5)))
                .andExpect(jsonPath("$.requirements[0].type").value("PREREQUISITE"))
                .andExpect(jsonPath("$.requirements[0].key").value(COMBO4))
                .andExpect(jsonPath("$.requirements[0].met").value(false))
                .andExpect(jsonPath("$.requirements[1].type").value("STAT_POINTS"))
                .andExpect(jsonPath("$.requirements[1].key", nullValue()))
                .andExpect(jsonPath("$.requirements[4].type").value("BEST_CLEAR_TIME"))
                .andExpect(jsonPath("$.requirements[4].upperBound").value(true))
                .andExpect(jsonPath("$.requirements[4].met").value(true));

        assertNothingChanged(accountId, 5);
    }

    // 확인(E2): 없는 스킬(서버가 판정하지 않는 DA 스킬 포함)은 400 SKILL_UNKNOWN, errors에 skillTag
    @Test
    void 없는_스킬은_400() throws Exception {
        String token = signupAndLogin("SkillApi04", "스킬해금04");
        long accountId = accountId("SkillApi04");
        setPoints(accountId, 5);

        unlock(token, body(newId(), "Account.Skill.HeavyAttack"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SKILL_UNKNOWN"))
                .andExpect(jsonPath("$.errors[0].field").value("skillTag"));
        unlock(token, "{\"requestId\": \"" + newId() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertNothingChanged(accountId, 5);
    }

    // 확인(E3): 새 requestId로 이미 해금한 스킬을 다시 요청하면 409 SKILL_ALREADY_UNLOCKED, 포인트는 한 번만
    @Test
    void 이미_해금한_스킬을_새_요청으로_다시_하면_409() throws Exception {
        String token = signupAndLogin("SkillApi05", "스킬해금05");
        long accountId = accountId("SkillApi05");
        setPoints(accountId, 5);
        setTotal(accountId, "stage_play.cleared", 1);
        unlock(token, body(newId(), COMBO4)).andExpect(status().isOk());

        unlock(token, body(newId(), COMBO4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKILL_ALREADY_UNLOCKED"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(points(accountId)).isEqualTo(4);
    }

    // 확인(E5·SF-5): 같은 requestId·같은 본문 두 번 → 두 번째도 처음 응답(SAVED) 그대로, 포인트는 한 번만
    @Test
    void 같은_요청을_두_번_보내도_한_번만_반영() throws Exception {
        String token = signupAndLogin("SkillApi06", "스킬해금06");
        long accountId = accountId("SkillApi06");
        setPoints(accountId, 5);
        setTotal(accountId, "stage_play.cleared", 1);
        String body = body(newId(), COMBO4);

        String first = unlock(token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = unlock(token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((Object) JsonPath.read(second, "$.data")).isEqualTo(JsonPath.read(first, "$.data"));
        assertThat(points(accountId)).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isEqualTo(1);
    }

    // 확인(DB 방어): Redis 기억이 사라진 뒤 같은 requestId가 오면 해금 줄의 request_id로 알아채 200 ALREADY_SAVED(README 재전송 규칙)
    @Test
    void 기억이_사라진_뒤_같은_요청은_ALREADY_SAVED() throws Exception {
        String token = signupAndLogin("SkillApi07", "스킬해금07");
        long accountId = accountId("SkillApi07");
        setPoints(accountId, 5);
        setTotal(accountId, "stage_play.cleared", 1);
        String requestId = newId();
        unlock(token, body(requestId, COMBO4)).andExpect(status().isOk());
        redis.delete("pw01:idem:" + accountId + ":" + requestId);

        unlock(token, body(requestId, COMBO4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saveStatus").value("ALREADY_SAVED"))
                .andExpect(jsonPath("$.data.result.statPoints").value(4));

        assertThat(points(accountId)).isEqualTo(4);
    }

    // 확인(조건별 경계값, K2로 봄): 목표 −1은 미충족, 목표는 충족. 최고 클리어는 "이하"이고 기록이 없으면 current null·미충족.
    //            실패하면 StatTotalEvaluator의 scale(밀리초 → 초)·비교 방향·missingIsZero를 의심
    @Test
    void 조건마다_경계값을_판정한다() throws Exception {
        String token = signupAndLogin("SkillApi08", "스킬해금08");
        long accountId = accountId("SkillApi08");
        setLevel(accountId, 3);
        setPoints(accountId, 2);
        setTotal(accountId, "stage_play.play_time_ms", 119_999);
        setTotal(accountId, "stage_play.kills", 9);
        setTotal(accountId, "stage_play.max_wave", 2);

        skills(token)
                // DodgeMastery: [STAT_POINTS, ACCOUNT_LEVEL, PLAY_TIME_SECONDS]
                .andExpect(jsonPath("$.data.skills[1].requirements[1].met").value(true))
                .andExpect(jsonPath("$.data.skills[1].requirements[2].current").value(119.999))
                .andExpect(jsonPath("$.data.skills[1].requirements[2].met").value(false))
                // CounterPlus: [PREREQUISITE, STAT_POINTS, TOTAL_KILLS, MAX_WAVE_REACHED, BEST_CLEAR_TIME]
                .andExpect(jsonPath("$.data.skills[2].requirements[2].met").value(false))
                .andExpect(jsonPath("$.data.skills[2].requirements[3].met").value(false))
                .andExpect(jsonPath("$.data.skills[2].requirements[4].current", nullValue()))
                .andExpect(jsonPath("$.data.skills[2].requirements[4].met").value(false));

        setTotal(accountId, "stage_play.play_time_ms", 120_000);
        setTotal(accountId, "stage_play.kills", 10);
        setTotal(accountId, "stage_play.max_wave", 3);
        setTotal(accountId, "stage_play.best_clear_ms", 900_001);
        skills(token)
                .andExpect(jsonPath("$.data.skills[1].requirements[2].met").value(true))
                .andExpect(jsonPath("$.data.skills[2].requirements[2].met").value(true))
                .andExpect(jsonPath("$.data.skills[2].requirements[3].met").value(true))
                .andExpect(jsonPath("$.data.skills[2].requirements[4].current").value(900.001))
                .andExpect(jsonPath("$.data.skills[2].requirements[4].met").value(false));

        setTotal(accountId, "stage_play.best_clear_ms", 900_000);
        skills(token).andExpect(jsonPath("$.data.skills[2].requirements[4].met").value(true));
    }

    // 확인(K2 상태 4가지): 해금함 UNLOCKED(requirements 빈 배열), 다 됨 UNLOCKABLE, 포인트만 모자람 NEED_POINTS, 조건 미충족 LOCKED
    @Test
    void 진행도_조회는_상태를_네_가지로_준다() throws Exception {
        String token = signupAndLogin("SkillApi09", "스킬해금09");
        long accountId = accountId("SkillApi09");
        setLevel(accountId, 3);
        setPoints(accountId, 3);
        setTotal(accountId, "stage_play.cleared", 1);
        setTotal(accountId, "stage_play.play_time_ms", 200_000);
        unlock(token, body(newId(), COMBO4)).andExpect(status().isOk());

        skills(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statPoints").value(2))
                .andExpect(jsonPath("$.data.skills[0].skillTag").value(COMBO4))
                .andExpect(jsonPath("$.data.skills[0].state").value("UNLOCKED"))
                .andExpect(jsonPath("$.data.skills[0].requirements", hasSize(0)))
                .andExpect(jsonPath("$.data.skills[1].state").value("UNLOCKABLE"))
                .andExpect(jsonPath("$.data.skills[1].cost").value(2))
                .andExpect(jsonPath("$.data.skills[2].state").value("LOCKED"));

        setPoints(accountId, 1);
        skills(token).andExpect(jsonPath("$.data.skills[1].state").value("NEED_POINTS"));
    }

    // 확인(동시): 같은 스킬을 다른 requestId로 동시에 둘 → 하나 200, 하나 409 SKILL_ALREADY_UNLOCKED, 포인트는 한 번만.
    //            실패하면 account_skill 기본 키 위반 → ProgressWriter 재시도 → 이미 해금 판정을 의심
    @Test
    void 같은_스킬을_동시에_해금해도_한_번() throws Exception {
        String token = signupAndLogin("SkillApi10", "스킬해금10");
        long accountId = accountId("SkillApi10");
        setPoints(accountId, 5);
        setTotal(accountId, "stage_play.cleared", 1);

        List<Integer> statuses = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> unlock(token, body(newId(), COMBO4)).andReturn().getResponse().getStatus(),
                    () -> unlock(token, body(newId(), COMBO4)).andReturn().getResponse().getStatus());
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(points(accountId)).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM account_skill WHERE account_id = ?", accountId)).isEqualTo(1);
    }

    // 확인(누적 키 3개): 결과를 낼 때마다 플레이 시간은 더하고, 웨이브는 최댓값, 클리어 시간은 클리어만 최솟값. 같은 결과 두 번은 한 번만.
    //            실패하면 AccountService.grantStageReward의 add·max·min, StageResultService가 넘기는 값을 의심
    @Test
    void 결과를_내면_해금_재료가_쌓인다() throws Exception {
        String token = signupAndLogin("SkillApi11", "스킬해금11");
        long accountId = accountId("SkillApi11");

        String firstPlay = startPlayOk(token);
        String firstResult = resultBody(newId(), true, 5, 1.5);
        submit(token, firstPlay, firstResult).andExpect(status().isOk());
        submit(token, startPlayOk(token), resultBody(newId(), false, 2, 2.0)).andExpect(status().isOk());
        submit(token, startPlayOk(token), resultBody(newId(), true, 5, 1.2)).andExpect(status().isOk());
        submit(token, firstPlay, firstResult).andExpect(status().isOk());

        assertThat(total(accountId, "stage_play.play_time_ms")).isEqualTo(4700);
        assertThat(total(accountId, "stage_play.max_wave")).isEqualTo(5);
        assertThat(total(accountId, "stage_play.best_clear_ms")).isEqualTo(1200);
        assertThat(total(accountId, "stage_play.cleared")).isEqualTo(2);
        skills(token)
                .andExpect(jsonPath("$.data.skills[2].requirements[4].current").value(1.2))
                .andExpect(jsonPath("$.data.skills[2].requirements[4].met").value(true));
    }

    // 확인: 토큰 없으면 401(K1·K2)
    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/accounts/me/skill-unlocks").contentType(MediaType.APPLICATION_JSON)
                        .content(body(newId(), COMBO4)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/accounts/me/skills")).andExpect(status().isUnauthorized());
    }

    private void assertNothingChanged(long accountId, int expectedPoints) {
        assertThat(points(accountId)).isEqualTo(expectedPoints);
        assertThat(count("SELECT COUNT(*) FROM account_skill WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_ledger WHERE account_id = ?", accountId)).isZero();
    }

    private ResultActions unlock(String token, String body) throws Exception {
        return mockMvc.perform(post("/accounts/me/skill-unlocks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions skills(String token) throws Exception {
        return mockMvc.perform(get("/accounts/me/skills").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static String body(String requestId, String skillTag) {
        return "{\"requestId\": \"" + requestId + "\", \"skillTag\": \"" + skillTag + "\"}";
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

    /** 플레이 시간은 짧게(시작 직후라도 경과 + 여유 60초 안) */
    private static String resultBody(String requestId, boolean cleared, int reachedWave, double playTimeSeconds) {
        return """
                {"requestId": "%s", "stageId": "%s", "difficulty": 0, "cleared": %s, "reachedWave": %d,
                 "totalWaveCount": 5, "playTimeSeconds": %s, "earnedGold": 120, "killCount": 4}
                """.formatted(requestId, STAGE_1, cleared, reachedWave, playTimeSeconds);
    }

    private void setPoints(long accountId, int points) {
        jdbc.update("UPDATE account_progress SET stat_points = ? WHERE account_id = ?", points, accountId);
    }

    private void setLevel(long accountId, int level) {
        jdbc.update("UPDATE account_progress SET level = ? WHERE account_id = ?", level, accountId);
    }

    private void setTotal(long accountId, String key, long value) {
        jdbc.update("""
                INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE total = VALUES(total)""", accountId, key, value);
    }

    private long total(long accountId, String key) {
        Long value = jdbc.query("SELECT total FROM account_stat_total WHERE account_id = ? AND stat_key = ?",
                rs -> rs.next() ? rs.getLong(1) : null, accountId, key);
        return value == null ? -1 : value;
    }

    private long points(long accountId) {
        return count("SELECT stat_points FROM account_progress WHERE account_id = ?", accountId);
    }

    private long version(long accountId) {
        return count("SELECT version FROM account_progress WHERE account_id = ?", accountId);
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
