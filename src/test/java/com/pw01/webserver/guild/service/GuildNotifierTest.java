package com.pw01.webserver.guild.service;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.common.error.ApiException;
import com.pw01.webserver.guild.GuildTestAccounts;
import com.pw01.webserver.guild.dto.GuildActionRequest;
import com.pw01.webserver.guild.dto.GuildJoinRequestCreateRequest;
import com.pw01.webserver.realtime.service.RealtimeConnection;
import com.pw01.webserver.realtime.service.RealtimeConnectionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 길드 실시간 알림(realtime-api.md v1.4 GUILD_*)이 커밋 뒤 맞는 사람에게 맞는 data로 가는지 본다.
 * 이 서버 연결 목록(RealtimeConnectionRegistry)에 가짜 WebSocket을 등록하고, 실제로 보내진 JSON을 잡아 읽는다.
 * 실패하면 GuildMembershipTx의 notice(받는 사람·data), GuildNotifier의 AFTER_COMMIT, RealtimeMessageType 이름을 먼저 의심한다.
 */
@IntegrationTest
class GuildNotifierTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    @Autowired
    GuildCommandService commands;

    @Autowired
    RealtimeConnectionRegistry registry;

    @Autowired
    JdbcTemplate jdbc;

    private final List<RealtimeConnection> connections = new ArrayList<>();

    @BeforeEach
    void resetCount() {
        GuildTestAccounts.resetGuildCount(jdbc);
    }

    @AfterEach
    void disconnect() {
        connections.forEach(registry::unregister);
        connections.clear();
    }

    // 확인: 신청 → 길드장만 GUILD_JOIN_REQUESTED(유효 신청 수 포함), 신청자 자신은 받지 않음
    @Test
    void 신청은_길드장에게() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long applicant = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "알림신청" + GuildTestAccounts.suffix(), leader, 1, 30);
        WebSocketSession leaderSocket = connect(leader);
        WebSocketSession applicantSocket = connect(applicant);

        commands.apply(applicant, new GuildJoinRequestCreateRequest(uuid(), String.valueOf(guildId)));

        Map<String, Object> message = only(leaderSocket);
        assertThat(message.get("type")).isEqualTo("GUILD_JOIN_REQUESTED");
        assertThat(JsonPath.<String>read(message, "$.data.guildId")).isEqualTo(String.valueOf(guildId));
        assertThat(JsonPath.<String>read(message, "$.data.accountId")).isEqualTo(String.valueOf(applicant));
        assertThat(JsonPath.<Integer>read(message, "$.data.pendingCount")).isEqualTo(1);
        assertThat(sent(applicantSocket)).isEmpty();
    }

    // 확인: 수락 → 길드원 전부(새 사람 포함)에게 GUILD_MEMBER_JOINED, memberCount는 반영 뒤 값
    @Test
    void 수락은_길드원_전부에게() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long applicant = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "알림수락" + GuildTestAccounts.suffix(), leader, 1, 30);
        long requestId = request(guildId, applicant);
        WebSocketSession leaderSocket = connect(leader);
        WebSocketSession applicantSocket = connect(applicant);

        commands.approve(leader, String.valueOf(requestId), action());

        for (WebSocketSession socket : List.of(leaderSocket, applicantSocket)) {
            Map<String, Object> message = only(socket);
            assertThat(message.get("type")).isEqualTo("GUILD_MEMBER_JOINED");
            assertThat(JsonPath.<String>read(message, "$.data.accountId")).isEqualTo(String.valueOf(applicant));
            assertThat(JsonPath.<Integer>read(message, "$.data.memberCount")).isEqualTo(2);
        }
    }

    // 확인: 거절 → 신청자에게 GUILD_JOIN_REJECTED(길드 이름 포함)
    @Test
    void 거절은_신청자에게() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long applicant = GuildTestAccounts.account(jdbc);
        String name = "알림거절" + GuildTestAccounts.suffix();
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, name, leader, 1, 30);
        long requestId = request(guildId, applicant);
        WebSocketSession applicantSocket = connect(applicant);

        commands.reject(leader, String.valueOf(requestId), action());

        Map<String, Object> message = only(applicantSocket);
        assertThat(message.get("type")).isEqualTo("GUILD_JOIN_REJECTED");
        assertThat(JsonPath.<String>read(message, "$.data.guildName")).isEqualTo(name);
        assertThat(JsonPath.<String>read(message, "$.data.joinRequestId")).isEqualTo(String.valueOf(requestId));
    }

    // 확인: 탈퇴 → 남은 길드원과 나간 사람에게 GUILD_MEMBER_LEFT(reason LEFT, 줄어든 memberCount)
    @Test
    void 탈퇴는_남은_길드원과_나간_사람에게() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long member = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "알림탈퇴" + GuildTestAccounts.suffix(), leader, 2, 30);
        join(member, guildId);
        WebSocketSession leaderSocket = connect(leader);
        WebSocketSession memberSocket = connect(member);

        commands.leave(member, action());

        for (WebSocketSession socket : List.of(leaderSocket, memberSocket)) {
            Map<String, Object> message = only(socket);
            assertThat(message.get("type")).isEqualTo("GUILD_MEMBER_LEFT");
            assertThat(JsonPath.<String>read(message, "$.data.reason")).isEqualTo("LEFT");
            assertThat(JsonPath.<Integer>read(message, "$.data.memberCount")).isEqualTo(1);
        }
    }

    // 확인: 해체 → 해체 직전 길드원과 그 길드 신청자에게 GUILD_DISBANDED, 상관없는 사람은 받지 않음
    @Test
    void 해체는_길드원과_신청자에게() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long member = GuildTestAccounts.account(jdbc);
        long applicant = GuildTestAccounts.account(jdbc);
        long outsider = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "알림해체" + GuildTestAccounts.suffix(), leader, 2, 30);
        join(member, guildId);
        request(guildId, applicant);
        jdbc.update("UPDATE guild_counter SET guild_count = 1 WHERE counter_id = 1");
        List<WebSocketSession> receivers = List.of(connect(leader), connect(member), connect(applicant));
        WebSocketSession outsiderSocket = connect(outsider);

        commands.disband(leader, action());

        for (WebSocketSession socket : receivers) {
            Map<String, Object> message = only(socket);
            assertThat(message.get("type")).isEqualTo("GUILD_DISBANDED");
            assertThat(JsonPath.<String>read(message, "$.data.guildId")).isEqualTo(String.valueOf(guildId));
        }
        assertThat(sent(outsiderSocket)).isEmpty();
    }

    // 확인: 거절된(롤백된) 요청은 알림이 나가지 않는다 — 정원이 찬 길드의 수락은 409이고 아무도 받지 않음(AFTER_COMMIT)
    @Test
    void 실패한_요청은_알림_없음() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long applicant = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "알림실패" + GuildTestAccounts.suffix(), leader, 2, 2);
        long requestId = request(guildId, applicant);
        WebSocketSession leaderSocket = connect(leader);
        WebSocketSession applicantSocket = connect(applicant);

        assertThatThrownBy(() -> commands.approve(leader, String.valueOf(requestId), action()))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "GUILD_FULL");

        assertThat(sent(leaderSocket)).isEmpty();
        assertThat(sent(applicantSocket)).isEmpty();
    }

    private WebSocketSession connect(long accountId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("guild-test-" + accountId);
        RealtimeConnection connection = new RealtimeConnection(socket, accountId, TOKEN);
        registry.register(connection);
        connections.add(connection);
        return socket;
    }

    /** 이 소켓으로 보내진 메시지(JSON → Map) 전부 */
    private static List<Map<String, Object>> sent(WebSocketSession socket) throws Exception {
        if (mockingDetails(socket).getInvocations().stream()
                .noneMatch(invocation -> invocation.getMethod().getName().equals("sendMessage"))) {
            return List.of();
        }
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, atLeast(1)).sendMessage(captor.capture());
        return captor.getAllValues().stream()
                .map(message -> JsonPath.<Map<String, Object>>read(message.getPayload(), "$"))
                .toList();
    }

    /** 이 소켓으로 정확히 하나 보내졌다고 보고 그 메시지 */
    private static Map<String, Object> only(WebSocketSession socket) throws Exception {
        List<Map<String, Object>> messages = sent(socket);
        assertThat(messages).hasSize(1);
        return messages.getFirst();
    }

    private void join(long accountId, long guildId) {
        jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, ?, 'MEMBER', NOW(3), 0, NOW(3), NOW(3))""", accountId, guildId);
    }

    private long request(long guildId, long accountId) {
        jdbc.update("""
                INSERT INTO guild_join_request (guild_id, account_id, kind, created_at, updated_at)
                VALUES (?, ?, 'APPLICATION', NOW(3), NOW(3))""", guildId, accountId);
        return jdbc.queryForObject(
                "SELECT guild_join_request_id FROM guild_join_request WHERE guild_id = ? AND account_id = ?",
                Long.class, guildId, accountId);
    }

    private static GuildActionRequest action() {
        return new GuildActionRequest(uuid());
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

}
