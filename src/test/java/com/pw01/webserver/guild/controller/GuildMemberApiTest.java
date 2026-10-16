package com.pw01.webserver.guild.controller;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.common.chat.GuildDisbandedEvent;
import com.pw01.webserver.common.chat.GuildMemberJoinedEvent;
import com.pw01.webserver.common.chat.GuildMemberLeftEvent;
import com.pw01.webserver.guild.GuildTestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 받은 신청·수락·거절·탈퇴·해체(guild-api.md G7·G8·G9·G11·G12)와 길드 생애 이벤트(common/chat) 발행을 HTTP로 확인한다.
 * 길드는 만들기 API로 만든다(guild_counter가 함께 올라가야 해체 때 −1이 맞음). 신청 행 등 나머지 상태는 SQL로 준비한다.
 * 실패하면 GuildMembershipTx(approve·leave·disbandGuild)의 순서, GuildCommandService.runAction의 멱등 키, 이벤트 발행 위치를 먼저 의심한다.
 */
@IntegrationTest
@RecordApplicationEvents
class GuildMemberApiTest {

    private static final String PASSWORD = "password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void resetCount() {
        GuildTestAccounts.resetGuildCount(jdbc);
    }

    // 확인: G7 받은 신청 — 길드장은 유효한 신청만 오래된 순, 길드원 403 NOT_LEADER, 미소속 403 NOT_MEMBER
    @Test
    void 받은_신청() throws Exception {
        Session leader = signupAndLogin(true);
        long guildId = createGuild(leader);
        long a = GuildTestAccounts.account(jdbc);
        long b = GuildTestAccounts.account(jdbc);
        long expired = GuildTestAccounts.account(jdbc);
        insertRequest(guildId, a, Duration.ofHours(2));
        insertRequest(guildId, b, Duration.ofHours(1));
        insertRequest(guildId, expired, Duration.ofDays(4));

        mockMvc.perform(get("/accounts/me/guild/join-requests").header(HttpHeaders.AUTHORIZATION, leader.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinRequests[*].accountId", contains(String.valueOf(a), String.valueOf(b))))
                .andExpect(jsonPath("$.data.joinRequests[0].nickname").isString());

        Session member = signupAndLogin(false);
        joinDirect(member.accountId(), guildId);
        expectCode(mockMvc.perform(get("/accounts/me/guild/join-requests")
                .header(HttpHeaders.AUTHORIZATION, member.bearer())), 403, "GUILD_NOT_LEADER");
        Session loner = signupAndLogin(false);
        expectCode(mockMvc.perform(get("/accounts/me/guild/join-requests")
                .header(HttpHeaders.AUTHORIZATION, loner.bearer())), 403, "GUILD_NOT_MEMBER");
    }

    // 확인: 수락 — 새 길드원·인원 2, 신청자의 다른 신청 삭제, GuildMemberJoinedEvent 발행, 같은 requestId 재전송은 처음 응답
    @Test
    void 수락() throws Exception {
        Session leader = signupAndLogin(true);
        long guildId = createGuild(leader);
        long applicant = GuildTestAccounts.account(jdbc);
        long requestId = insertRequest(guildId, applicant, Duration.ZERO);
        long otherGuild = GuildTestAccounts.guildWithLeader(jdbc, "다른곳" + GuildTestAccounts.suffix(),
                GuildTestAccounts.account(jdbc), 1, 30);
        insertRequest(otherGuild, applicant, Duration.ZERO);
        String key = UUID.randomUUID().toString();

        action(leader, "/accounts/me/guild/join-requests/" + requestId + "/approve", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.accountId").value(String.valueOf(applicant)))
                .andExpect(jsonPath("$.data.member.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.memberCount").value(2));
        action(leader, "/accounts/me/guild/join-requests/" + requestId + "/approve", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount").value(2));

        assertThat(guildIdOf(applicant)).isEqualTo(guildId);
        assertThat(memberCount(guildId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild_join_request WHERE account_id = ?",
                Integer.class, applicant)).isZero();
        assertThat(events.stream(GuildMemberJoinedEvent.class))
                .containsExactly(new GuildMemberJoinedEvent(String.valueOf(guildId), applicant));
    }

    // 확인: 수락 거절 경로 — 없는 신청 404, 정원 409, 신청자가 이미 길드 409, 길드원 403, 같은 requestId로 다른 신청 409
    @Test
    void 수락_거절_경로() throws Exception {
        Session leader = signupAndLogin(true);
        long guildId = createGuild(leader);
        expectCode(action(leader, "/accounts/me/guild/join-requests/999999999/approve", uuid()),
                404, "GUILD_JOIN_REQUEST_NOT_FOUND");

        long joined = GuildTestAccounts.account(jdbc);
        long joinedRequest = insertRequest(guildId, joined, Duration.ZERO);
        long elsewhere = GuildTestAccounts.guildWithLeader(jdbc, "이미" + GuildTestAccounts.suffix(),
                GuildTestAccounts.account(jdbc), 1, 30);
        joinDirect(joined, elsewhere);
        expectCode(action(leader, "/accounts/me/guild/join-requests/" + joinedRequest + "/approve", uuid()),
                409, "GUILD_ALREADY_JOINED");

        long a = GuildTestAccounts.account(jdbc);
        long b = GuildTestAccounts.account(jdbc);
        long ra = insertRequest(guildId, a, Duration.ZERO);
        long rb = insertRequest(guildId, b, Duration.ZERO);
        String reused = uuid();
        action(leader, "/accounts/me/guild/join-requests/" + ra + "/approve", reused).andExpect(status().isOk());
        expectCode(action(leader, "/accounts/me/guild/join-requests/" + rb + "/approve", reused),
                409, "IDEMPOTENCY_KEY_REUSED");

        jdbc.update("UPDATE guild SET max_members = 2 WHERE guild_id = ?", guildId);
        expectCode(action(leader, "/accounts/me/guild/join-requests/" + rb + "/approve", uuid()), 409, "GUILD_FULL");

        Session member = signupAndLogin(false);
        jdbc.update("UPDATE guild SET max_members = 30 WHERE guild_id = ?", guildId);
        joinDirect(member.accountId(), guildId);
        expectCode(action(member, "/accounts/me/guild/join-requests/" + rb + "/approve", uuid()),
                403, "GUILD_NOT_LEADER");
    }

    // 확인: 거절 — 신청 삭제, 다시 거절하면 404
    @Test
    void 거절() throws Exception {
        Session leader = signupAndLogin(true);
        long guildId = createGuild(leader);
        long requestId = insertRequest(guildId, GuildTestAccounts.account(jdbc), Duration.ZERO);

        action(leader, "/accounts/me/guild/join-requests/" + requestId + "/reject", uuid())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinRequestId").value(String.valueOf(requestId)));
        expectCode(action(leader, "/accounts/me/guild/join-requests/" + requestId + "/reject", uuid()),
                404, "GUILD_JOIN_REQUEST_NOT_FOUND");
    }

    // 확인: 탈퇴 — 길드원은 인원 −1·무소속·LEFT 이벤트, 길드원이 있는 길드장은 409, 미소속은 403
    @Test
    void 탈퇴() throws Exception {
        Session leader = signupAndLogin(true);
        long guildId = createGuild(leader);
        Session member = signupAndLogin(false);
        joinDirect(member.accountId(), guildId);
        jdbc.update("UPDATE guild SET member_count = 2 WHERE guild_id = ?", guildId);

        expectCode(action(leader, "/accounts/me/guild/leave", uuid()), 409, "GUILD_LEADER_CANNOT_LEAVE");
        action(member, "/accounts/me/guild/leave", uuid())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guildId").value(String.valueOf(guildId)))
                .andExpect(jsonPath("$.data.disbanded").value(false));
        expectCode(action(member, "/accounts/me/guild/leave", uuid()), 403, "GUILD_NOT_MEMBER");

        assertThat(guildIdOf(member.accountId())).isNull();
        assertThat(memberCount(guildId)).isEqualTo(1);
        assertThat(events.stream(GuildMemberLeftEvent.class)).containsExactly(new GuildMemberLeftEvent(
                String.valueOf(guildId), member.accountId(), GuildMemberLeftEvent.Reason.LEFT));
    }

    // 확인: 혼자인 길드장의 탈퇴는 해체 — disbanded true, guild 행 삭제, 길드 수 −1
    @Test
    void 혼자인_길드장_탈퇴는_해체() throws Exception {
        Session leader = signupAndLogin(true);
        long guildId = createGuild(leader);

        action(leader, "/accounts/me/guild/leave", uuid())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbanded").value(true));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild WHERE guild_id = ?", Integer.class, guildId)).isZero();
        assertThat(jdbc.queryForObject("SELECT guild_count FROM guild_counter", Integer.class)).isZero();
        assertThat(events.stream(GuildDisbandedEvent.class)).hasSize(1);
    }

    // 확인: 해체 — 길드원 모두 무소속, 받은 신청 삭제, guild 행 삭제, 길드 수 −1, 해체 직전 길드원이 담긴 이벤트, 같은 이름 다시 쓸 수 있음
    @Test
    void 해체() throws Exception {
        Session leader = signupAndLogin(true);
        String name = "해체" + GuildTestAccounts.suffix();
        long guildId = createGuild(leader, name);
        Session member = signupAndLogin(false);
        joinDirect(member.accountId(), guildId);
        jdbc.update("UPDATE guild SET member_count = 2 WHERE guild_id = ?", guildId);
        long applicant = GuildTestAccounts.account(jdbc);
        insertRequest(guildId, applicant, Duration.ZERO);

        expectCode(action(member, "/accounts/me/guild/disband", uuid()), 403, "GUILD_NOT_LEADER");
        action(leader, "/accounts/me/guild/disband", uuid())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guildId").value(String.valueOf(guildId)));

        assertThat(guildIdOf(leader.accountId())).isNull();
        assertThat(guildIdOf(member.accountId())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild_join_request WHERE guild_id = ?",
                Integer.class, guildId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild WHERE guild_id = ?", Integer.class, guildId)).isZero();
        assertThat(jdbc.queryForObject("SELECT guild_count FROM guild_counter", Integer.class)).isZero();
        GuildDisbandedEvent disbanded = events.stream(GuildDisbandedEvent.class).findFirst().orElseThrow();
        assertThat(disbanded.guildId()).isEqualTo(String.valueOf(guildId));
        assertThat(disbanded.formerMemberIds()).containsExactlyInAnyOrder(leader.accountId(), member.accountId());

        createGuild(leader, name);
    }

    private long createGuild(Session leader) throws Exception {
        return createGuild(leader, "길" + GuildTestAccounts.suffix());
    }

    private long createGuild(Session leader, String name) throws Exception {
        String body = mockMvc.perform(post("/guilds")
                        .header(HttpHeaders.AUTHORIZATION, leader.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "name": "%s"}""".formatted(uuid(), name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(JsonPath.read(body, "$.data.guild.guildId"));
    }

    private ResultActions action(Session session, String uri, String requestId) throws Exception {
        return mockMvc.perform(post(uri)
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s"}""".formatted(requestId)));
    }

    private void expectCode(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    /** SQL로 길드원 만들기(account_guild 행만. 인원 칼럼은 필요한 테스트가 직접 맞춘다) */
    private void joinDirect(long accountId, long guildId) {
        jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, ?, 'MEMBER', NOW(3), 0, NOW(3), NOW(3))
                ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id), role = 'MEMBER', joined_at = NOW(3)""",
                accountId, guildId);
    }

    private long insertRequest(long guildId, long accountId, Duration age) {
        jdbc.update("""
                INSERT INTO guild_join_request (guild_id, account_id, kind, created_at, updated_at)
                VALUES (?, ?, 'APPLICATION', NOW(3) - INTERVAL ? SECOND, NOW(3))""", guildId, accountId, age.toSeconds());
        return jdbc.queryForObject("SELECT guild_join_request_id FROM guild_join_request WHERE guild_id = ? AND account_id = ?",
                Long.class, guildId, accountId);
    }

    private Long guildIdOf(long accountId) {
        return jdbc.queryForObject("SELECT guild_id FROM account_guild WHERE account_id = ?", Long.class, accountId);
    }

    private int memberCount(long guildId) {
        return jdbc.queryForObject("SELECT member_count FROM guild WHERE guild_id = ?", Integer.class, guildId);
    }

    private Session signupAndLogin(boolean cleared) throws Exception {
        String loginId = "GdM" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s", "nickname": "%s"}
                                """.formatted(loginId, PASSWORD, "길원" + loginId.substring(3, 9))))
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

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private record Session(String bearer, Long accountId) {
    }

}
