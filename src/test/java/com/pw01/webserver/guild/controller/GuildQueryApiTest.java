package com.pw01.webserver.guild.controller;

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

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 길드 조회 API(guild-api.md G1·G2·G4)를 HTTP로 확인한다. 길드·소속·신청 행은 SQL로 직접 만든다(쓰기 API는 W5·W6).
 * 실패하면 GuildQueryService의 검증(name·cursor·size), GuildRepository.findPage, 응답 DTO 필드 이름을 먼저 의심한다.
 * 길드 목록은 모든 테스트가 같은 DB를 보므로 이름 앞부분(검색)으로 이 테스트의 길드만 고른다.
 */
@IntegrationTest
class GuildQueryApiTest {

    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    // 확인: 최근 생성 순, 이름 앞부분 검색, 커서로 다음 묶음, 마지막 묶음은 hasNext false·nextCursor null
    @Test
    void 목록은_최근_순_커서() throws Exception {
        Session viewer = signupAndLogin();
        long leader = insertAccount();
        long g1 = insertGuild("목록가1", leader, 1);
        long g2 = insertGuild("목록가2", leader, 1);
        long g3 = insertGuild("목록가3", leader, 1);

        String first = getAs(viewer, "/guilds?name=목록가&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guilds[*].guildId", contains(String.valueOf(g3), String.valueOf(g2))))
                .andExpect(jsonPath("$.data.guilds[0].name").value("목록가3"))
                .andExpect(jsonPath("$.data.guilds[0].leaderAccountId").value(String.valueOf(leader)))
                .andExpect(jsonPath("$.data.guilds[0].memberCount").value(1))
                .andExpect(jsonPath("$.data.guilds[0].maxMembers").value(30))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");

        getAs(viewer, "/guilds?name=목록가&size=2&cursor=" + cursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guilds[*].guildId", contains(String.valueOf(g1))))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    // 확인: 검색은 대소문자를 가린다(이름 칼럼이 utf8mb4_0900_bin)
    @Test
    void 검색은_대소문자_구분() throws Exception {
        Session viewer = signupAndLogin();
        long leader = insertAccount();
        insertGuild("CaseUp", leader, 1);
        insertGuild("caseUp", leader, 1);

        getAs(viewer, "/guilds?name=CaseU")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guilds[*].name", contains("CaseUp")));
    }

    // 확인: 잘못된 쿼리 값은 400 VALIDATION_FAILED, errors에 그 필드(%·_ 같은 글자는 이름 규칙에서 막혀 LIKE에 닿지 않음)
    @Test
    void 잘못된_쿼리는_400() throws Exception {
        Session viewer = signupAndLogin();

        expectInvalid(getAs(viewer, "/guilds?name=%25"), "name");
        expectInvalid(getAs(viewer, "/guilds?name=a_b"), "name");
        expectInvalid(getAs(viewer, "/guilds?name=" + "가".repeat(13)), "name");
        expectInvalid(getAs(viewer, "/guilds?cursor=abc"), "cursor");
        expectInvalid(getAs(viewer, "/guilds?size=0"), "size");
        expectInvalid(getAs(viewer, "/guilds?size=51"), "size");
        getAs(viewer, "/guilds?size=x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // 확인: G2 하나 — 있으면 요약, 숫자가 아니면 400, 없으면 404 GUILD_NOT_FOUND
    @Test
    void 길드_하나() throws Exception {
        Session viewer = signupAndLogin();
        long leader = insertAccount();
        long guildId = insertGuild("하나보기", leader, 3);

        getAs(viewer, "/guilds/" + guildId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guild.guildId").value(String.valueOf(guildId)))
                .andExpect(jsonPath("$.data.guild.memberCount").value(3))
                .andExpect(jsonPath("$.data.guild.leaderNickname").isString());
        expectInvalid(getAs(viewer, "/guilds/abc"), "guildId");
        getAs(viewer, "/guilds/999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUILD_NOT_FOUND"));
    }

    // 확인: G4 미소속 — guild null, members [], 만들기 조건 미충족(스테이지 1 클리어 전), 유효한 내 신청만(만료된 신청 제외)
    @Test
    void 내_길드_미소속() throws Exception {
        Session viewer = signupAndLogin();
        long leader = insertAccount();
        long open = insertGuild("신청받는곳", leader, 1);
        long old = insertGuild("만료된곳", leader, 1);
        insertRequest(open, viewer.accountId(), Duration.ZERO);
        insertRequest(old, viewer.accountId(), Duration.ofDays(4));

        getAs(viewer, "/accounts/me/guild")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guild").value(nullValue()))
                .andExpect(jsonPath("$.data.members", hasSize(0)))
                .andExpect(jsonPath("$.data.pendingJoinRequestCount").value(0))
                .andExpect(jsonPath("$.data.myJoinRequests", hasSize(1)))
                .andExpect(jsonPath("$.data.myJoinRequests[0].guildId").value(String.valueOf(open)))
                .andExpect(jsonPath("$.data.myJoinRequests[0].guildName").value("신청받는곳"))
                .andExpect(jsonPath("$.data.myJoinRequests[0].accountId").value(String.valueOf(viewer.accountId())))
                .andExpect(jsonPath("$.data.myJoinRequests[0].expiresAt").isString())
                .andExpect(jsonPath("$.data.create.allowed").value(false))
                .andExpect(jsonPath("$.data.create.requirements[0].type").value("STAGE_CLEARED"))
                .andExpect(jsonPath("$.data.create.requirements[0].stageId").value("stage.01.01"))
                .andExpect(jsonPath("$.data.create.requirements[0].met").value(false))
                .andExpect(jsonPath("$.data.rejoinAvailableAt").value(nullValue()));
    }

    // 확인: 스테이지 1을 클리어한 미소속이면 만들기 가능(allowed true)
    @Test
    void 클리어하면_만들기_가능() throws Exception {
        Session viewer = signupAndLogin();
        jdbc.update("""
                INSERT INTO account_stage_progress (account_id, stage_id, first_cleared_at, created_at, updated_at)
                VALUES (?, 'stage.01.01', NOW(3), NOW(3), NOW(3))""", viewer.accountId());

        getAs(viewer, "/accounts/me/guild")
                .andExpect(jsonPath("$.data.create.allowed").value(true))
                .andExpect(jsonPath("$.data.create.requirements[0].met").value(true));
    }

    // 확인: G4 길드장 — 내 역할, 길드장 먼저 그다음 들어온 순, 접속 표시(presence 키), 받은 유효 신청 수(만료 제외)
    @Test
    void 내_길드_길드장() throws Exception {
        Session viewer = signupAndLogin();
        long early = insertAccount();
        long late = insertAccount();
        long applicant = insertAccount();
        long expired = insertAccount();
        long guildId = insertGuild("길드장화면", viewer.accountId(), 3);
        joinGuild(early, guildId, "MEMBER", "2026-10-16 01:00:00.000");
        joinGuild(viewer.accountId(), guildId, "LEADER", "2026-10-16 02:00:00.000");
        joinGuild(late, guildId, "MEMBER", "2026-10-16 03:00:00.000");
        insertRequest(guildId, applicant, Duration.ZERO);
        insertRequest(guildId, expired, Duration.ofDays(4));
        redis.opsForValue().set("pw01:presence:" + late, "srv-test", Duration.ofSeconds(30));

        getAs(viewer, "/accounts/me/guild")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guild.guildId").value(String.valueOf(guildId)))
                .andExpect(jsonPath("$.data.guild.myRole").value("LEADER"))
                .andExpect(jsonPath("$.data.guild.memberCount").value(3))
                .andExpect(jsonPath("$.data.members[*].accountId", contains(String.valueOf(viewer.accountId()),
                        String.valueOf(early), String.valueOf(late))))
                .andExpect(jsonPath("$.data.members[0].role").value("LEADER"))
                .andExpect(jsonPath("$.data.members[*].online", contains(false, false, true)))
                .andExpect(jsonPath("$.data.pendingJoinRequestCount").value(1))
                .andExpect(jsonPath("$.data.myJoinRequests", hasSize(0)))
                .andExpect(jsonPath("$.data.create.allowed").value(false));
    }

    // 확인: G4 길드원은 받은 신청 수를 보지 않는다(0)
    @Test
    void 내_길드_길드원() throws Exception {
        Session viewer = signupAndLogin();
        long leader = insertAccount();
        long applicant = insertAccount();
        long guildId = insertGuild("길드원화면", leader, 2);
        joinGuild(leader, guildId, "LEADER", "2026-10-16 01:00:00.000");
        joinGuild(viewer.accountId(), guildId, "MEMBER", "2026-10-16 02:00:00.000");
        insertRequest(guildId, applicant, Duration.ZERO);

        getAs(viewer, "/accounts/me/guild")
                .andExpect(jsonPath("$.data.guild.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.data.pendingJoinRequestCount").value(0));
    }

    // 확인: 인증 없이는 401(공개 경로가 아님)
    @Test
    void 인증_없으면_401() throws Exception {
        mockMvc.perform(get("/guilds")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/accounts/me/guild")).andExpect(status().isUnauthorized());
    }

    private void expectInvalid(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    private ResultActions getAs(Session session, String uri) throws Exception {
        return mockMvc.perform(get(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()));
    }

    private Session signupAndLogin() throws Exception {
        String loginId = "GdQ" + UUID.randomUUID().toString().substring(0, 8);
        String nickname = "길조" + loginId.substring(3, 9);
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
        Long accountId = jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
        return new Session(JsonPath.read(body, "$.data.accessToken"), accountId);
    }

    /** SQL로 만든 계정(account_guild 행은 무소속으로 같이 넣음 — 가입 이벤트를 거치지 않으므로) */
    private long insertAccount() {
        String loginId = "GdA" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, "길계" + loginId.substring(3, 9));
        long accountId = jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
        jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, NULL, NULL, NULL, 0, NOW(3), NOW(3))""", accountId);
        return accountId;
    }

    private long insertGuild(String name, long leaderAccountId, int memberCount) {
        jdbc.update("""
                INSERT INTO guild (name, leader_account_id, member_count, max_members, version, created_at, updated_at)
                VALUES (?, ?, ?, 30, 0, NOW(3), NOW(3))""", name, leaderAccountId, memberCount);
        return jdbc.queryForObject("SELECT guild_id FROM guild WHERE name = ?", Long.class, name);
    }

    private void joinGuild(long accountId, long guildId, String role, String joinedAt) {
        jdbc.update("UPDATE account_guild SET guild_id = ?, role = ?, joined_at = ? WHERE account_id = ?",
                guildId, role, joinedAt, accountId);
    }

    /** 신청 행. age만큼 예전에 신청한 것으로 넣는다(유효 기간 3일을 넘기면 만료) */
    private void insertRequest(long guildId, long accountId, Duration age) {
        jdbc.update("""
                INSERT INTO guild_join_request (guild_id, account_id, kind, created_at, updated_at)
                VALUES (?, ?, 'APPLICATION', NOW(3) - INTERVAL ? SECOND, NOW(3))""",
                guildId, accountId, age.toSeconds());
    }

    private record Session(String token, Long accountId) {
    }

}
