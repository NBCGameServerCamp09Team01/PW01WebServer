package com.pw01.webserver.guild.controller;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.guild.GuildTestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 길드 만들기·가입 신청·신청 취소(guild-api.md G3·G5·G6)를 HTTP로 확인한다. 판정 순서와 오류 코드가 명세와 같은지 본다.
 * 실패하면 GuildMembershipTx의 판정 순서, GuildCommandService.translate(제약 이름 → 오류 코드), 요청 DTO의 정규식을 먼저 의심한다.
 * 서버 길드 수 상한(5)이 테스트 사이에 쌓이지 않게 매번 guild_counter를 0으로 되돌린다.
 */
@IntegrationTest
class GuildCommandApiTest {

    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetCount() {
        GuildTestAccounts.resetGuildCount(jdbc);
    }

    // 확인: 만들기 성공 — 201·Location, 내 길드(길드장·인원 1), 길드 수 +1, 내 대기 중 신청은 지워짐
    @Test
    void 만들기() throws Exception {
        Session me = signupAndLogin(true);
        long otherLeader = GuildTestAccounts.account(jdbc);
        long other = GuildTestAccounts.guildWithLeader(jdbc, "남의길드" + GuildTestAccounts.suffix(), otherLeader, 1, 30);
        apply(me, other).andExpect(status().isCreated());
        String name = "새길드" + GuildTestAccounts.suffix();

        create(me, UUID.randomUUID().toString(), name)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/accounts/me/guild"))
                .andExpect(jsonPath("$.data.guild.name").value(name))
                .andExpect(jsonPath("$.data.guild.myRole").value("LEADER"))
                .andExpect(jsonPath("$.data.guild.memberCount").value(1))
                .andExpect(jsonPath("$.data.guild.maxMembers").value(30))
                .andExpect(jsonPath("$.data.members", hasSize(1)))
                .andExpect(jsonPath("$.data.myJoinRequests", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT guild_count FROM guild_counter", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild_join_request WHERE account_id = ?",
                Integer.class, me.accountId())).isZero();
    }

    // 확인: 같은 requestId로 다시 보내면 처음 응답 그대로, 길드는 하나
    @Test
    void 같은_요청은_한_번() throws Exception {
        Session me = signupAndLogin(true);
        String requestId = UUID.randomUUID().toString();
        String name = "한번만" + GuildTestAccounts.suffix();

        String first = create(me, requestId, name).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String again = create(me, requestId, name).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(again, "$.data.guild.guildId"))
                .isEqualTo(JsonPath.read(first, "$.data.guild.guildId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild WHERE name = ?", Integer.class, name)).isEqualTo(1);
    }

    // 확인: 판정 순서대로 거절 — 조건 미충족(+requirements), 이미 길드, 이름 중복(대소문자 같을 때만), 상한
    @Test
    void 만들기_거절() throws Exception {
        Session notCleared = signupAndLogin(false);
        create(notCleared, UUID.randomUUID().toString(), "조건없음" + GuildTestAccounts.suffix())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_CREATE_REQUIREMENTS_NOT_MET"))
                .andExpect(jsonPath("$.requirements[0].type").value("STAGE_CLEARED"))
                .andExpect(jsonPath("$.requirements[0].met").value(false));

        Session leader = signupAndLogin(true);
        String name = "Dup" + GuildTestAccounts.suffix();
        create(leader, UUID.randomUUID().toString(), name).andExpect(status().isCreated());
        create(leader, UUID.randomUUID().toString(), "두번째" + GuildTestAccounts.suffix())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_ALREADY_JOINED"));

        Session another = signupAndLogin(true);
        create(another, UUID.randomUUID().toString(), name)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_NAME_DUPLICATED"));
        create(another, UUID.randomUUID().toString(), name.toLowerCase())
                .andExpect(status().isCreated());

        Session third = signupAndLogin(true);
        jdbc.update("UPDATE guild_counter SET guild_count = 5 WHERE counter_id = 1");
        create(third, UUID.randomUUID().toString(), "상한" + GuildTestAccounts.suffix())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILD_LIMIT_REACHED"));
    }

    // 확인: 이름 형식(1자·13자·연속 공백·앞 공백·기호)과 requestId 형식은 400 VALIDATION_FAILED
    @Test
    void 만들기_형식() throws Exception {
        Session me = signupAndLogin(true);
        for (String bad : new String[]{"가", "가".repeat(13), "새벽  기사", " 새벽", "새벽!"}) {
            create(me, UUID.randomUUID().toString(), bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }
        create(me, "not-uuid", "형식길드").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("requestId"));
    }

    // 확인: 신청 성공 — 201·Location, 응답 필드, 내 길드 화면의 myJoinRequests에 보임
    @Test
    void 신청() throws Exception {
        Session me = signupAndLogin(false);
        long leader = GuildTestAccounts.account(jdbc);
        String guildName = "신청대상" + GuildTestAccounts.suffix();
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, guildName, leader, 1, 30);

        String body = apply(me, guildId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.guildId").value(String.valueOf(guildId)))
                .andExpect(jsonPath("$.data.guildName").value(guildName))
                .andExpect(jsonPath("$.data.accountId").value(String.valueOf(me.accountId())))
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andReturn().getResponse().getContentAsString();
        String joinRequestId = JsonPath.read(body, "$.data.joinRequestId");

        mockMvc.perform(get("/accounts/me/guild").header(HttpHeaders.AUTHORIZATION, me.bearer()))
                .andExpect(jsonPath("$.data.myJoinRequests[0].joinRequestId").value(joinRequestId));
    }

    // 확인: 신청 거절 — 없는 길드 404, 같은 길드 409, 3곳 넘음 409, 정원 409, 이미 길드 409, 형식 400
    @Test
    void 신청_거절() throws Exception {
        Session me = signupAndLogin(false);
        long leader = GuildTestAccounts.account(jdbc);
        long g1 = GuildTestAccounts.guildWithLeader(jdbc, "거절1" + GuildTestAccounts.suffix(), leader, 1, 30);
        long g2 = GuildTestAccounts.guildWithLeader(jdbc, "거절2" + GuildTestAccounts.suffix(), GuildTestAccounts.account(jdbc), 1, 30);
        long g3 = GuildTestAccounts.guildWithLeader(jdbc, "거절3" + GuildTestAccounts.suffix(), GuildTestAccounts.account(jdbc), 1, 30);
        long g4 = GuildTestAccounts.guildWithLeader(jdbc, "거절4" + GuildTestAccounts.suffix(), GuildTestAccounts.account(jdbc), 1, 30);
        long full = GuildTestAccounts.guildWithLeader(jdbc, "가득" + GuildTestAccounts.suffix(), GuildTestAccounts.account(jdbc), 2, 2);

        expectCode(apply(me, 999_999_999L), 404, "GUILD_NOT_FOUND");
        expectCode(apply(me, full), 409, "GUILD_FULL");
        apply(me, g1).andExpect(status().isCreated());
        expectCode(apply(me, g1), 409, "GUILD_JOIN_REQUEST_DUPLICATED");
        apply(me, g2).andExpect(status().isCreated());
        apply(me, g3).andExpect(status().isCreated());
        expectCode(apply(me, g4), 409, "GUILD_JOIN_REQUEST_LIMIT");

        Session member = signupAndLogin(false);
        jdbc.update("UPDATE account_guild SET guild_id = ?, role = 'MEMBER', joined_at = NOW(3) WHERE account_id = ?",
                g1, member.accountId());
        expectCode(apply(member, g2), 409, "GUILD_ALREADY_JOINED");

        mockMvc.perform(post("/accounts/me/guild-join-requests")
                        .header(HttpHeaders.AUTHORIZATION, me.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "guildId": "abc"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guildId"));
    }

    // 확인: 만료된 같은 길드 신청은 지우고 새로 받으며, 만료된 신청은 3곳에 세지 않는다
    @Test
    void 만료된_신청은_새로() throws Exception {
        Session me = signupAndLogin(false);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "만료" + GuildTestAccounts.suffix(),
                GuildTestAccounts.account(jdbc), 1, 30);
        jdbc.update("""
                INSERT INTO guild_join_request (guild_id, account_id, kind, created_at, updated_at)
                VALUES (?, ?, 'APPLICATION', NOW(3) - INTERVAL 4 DAY, NOW(3))""", guildId, me.accountId());

        apply(me, guildId).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM guild_join_request
                 WHERE account_id = ? AND created_at > NOW(3) - INTERVAL 1 DAY""", Integer.class, me.accountId()))
                .isEqualTo(1);
    }

    // 확인: 취소 — 내 신청은 지우고 204, 다시 보내도 204, 남의 신청은 안 지워지고 204, 형식이 틀리면 400
    @Test
    void 취소() throws Exception {
        Session me = signupAndLogin(false);
        Session other = signupAndLogin(false);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "취소" + GuildTestAccounts.suffix(),
                GuildTestAccounts.account(jdbc), 1, 30);
        String mine = JsonPath.read(apply(me, guildId).andReturn().getResponse().getContentAsString(),
                "$.data.joinRequestId");
        String theirs = JsonPath.read(apply(other, guildId).andReturn().getResponse().getContentAsString(),
                "$.data.joinRequestId");

        cancel(me, mine).andExpect(status().isNoContent());
        cancel(me, mine).andExpect(status().isNoContent());
        cancel(me, theirs).andExpect(status().isNoContent());
        cancel(me, "abc").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild_join_request WHERE guild_join_request_id = ?",
                Integer.class, Long.parseLong(mine))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild_join_request WHERE guild_join_request_id = ?",
                Integer.class, Long.parseLong(theirs))).isEqualTo(1);
    }

    private void expectCode(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private ResultActions create(Session session, String requestId, String name) throws Exception {
        return mockMvc.perform(post("/guilds")
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "name": "%s"}""".formatted(requestId, name)));
    }

    private ResultActions apply(Session session, long guildId) throws Exception {
        return mockMvc.perform(post("/accounts/me/guild-join-requests")
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "guildId": "%d"}""".formatted(UUID.randomUUID(), guildId)));
    }

    private ResultActions cancel(Session session, String joinRequestId) throws Exception {
        return mockMvc.perform(delete("/accounts/me/guild-join-requests/" + joinRequestId)
                .header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }

    private Session signupAndLogin(boolean cleared) throws Exception {
        String loginId = "GdC" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s", "nickname": "%s"}
                                """.formatted(loginId, PASSWORD, "길명" + loginId.substring(3, 9))))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(loginId, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long accountId = jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
        if (cleared) {
            jdbc.update("""
                    INSERT INTO account_stage_progress (account_id, stage_id, first_cleared_at, created_at, updated_at)
                    VALUES (?, 'stage.01.01', NOW(3), NOW(3), NOW(3))""", accountId);
        }
        return new Session("Bearer " + JsonPath.read(body, "$.data.accessToken"), accountId);
    }

    private record Session(String bearer, Long accountId) {
    }

}
