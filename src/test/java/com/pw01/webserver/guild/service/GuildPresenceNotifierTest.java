package com.pw01.webserver.guild.service;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.guild.GuildTestAccounts;
import com.pw01.webserver.realtime.event.RealtimeConnectedEvent;
import com.pw01.webserver.realtime.event.RealtimeDisconnectedEvent;
import com.pw01.webserver.realtime.service.RealtimeConnection;
import com.pw01.webserver.realtime.service.RealtimeConnectionRegistry;
import com.pw01.webserver.realtime.service.RealtimeServerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 길드원 접속 실시간 표시(M23 Lv2, realtime-api.md PRESENCE_CHANGED): 연결 이벤트 → 접속 상태 키 → 바뀌었으면 본인을 뺀 길드원에게.
 * 연결 이벤트는 realtime이 내는 것과 같은 모양으로 직접 내고, 받는 쪽은 이 서버 연결 목록에 등록한 가짜 WebSocket으로 본다.
 * 실패하면 PresenceService의 바뀜 판정(setGet·Lua 결과), GuildPresenceNotifier의 받는 사람(본인 제외), GuildDirectory 조회를 먼저 의심한다.
 */
@IntegrationTest
class GuildPresenceNotifierTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    RealtimeConnectionRegistry registry;

    @Autowired
    RealtimeServerId serverId;

    @Autowired
    JdbcTemplate jdbc;

    private final List<RealtimeConnection> connections = new ArrayList<>();

    @AfterEach
    void disconnect() {
        connections.forEach(registry::unregister);
        connections.clear();
    }

    // 확인: 길드원이 처음 접속하면 다른 길드원만 online true를 받고, 본인과 다른 길드 사람은 받지 않는다
    @Test
    void 접속하면_다른_길드원에게() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long member = GuildTestAccounts.account(jdbc);
        long outsider = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "접속표시" + GuildTestAccounts.suffix(), leader, 2, 30);
        join(member, guildId);
        WebSocketSession leaderSocket = socket(leader);
        WebSocketSession memberSocket = socket(member);
        WebSocketSession outsiderSocket = socket(outsider);

        events.publishEvent(new RealtimeConnectedEvent(member, serverId.value()));

        Map<String, Object> message = only(leaderSocket);
        assertThat(message.get("type")).isEqualTo("PRESENCE_CHANGED");
        assertThat(JsonPath.<String>read(message, "$.data.guildId")).isEqualTo(String.valueOf(guildId));
        assertThat(JsonPath.<String>read(message, "$.data.accountId")).isEqualTo(String.valueOf(member));
        assertThat(JsonPath.<Boolean>read(message, "$.data.online")).isTrue();
        assertThat(sent(memberSocket)).isEmpty();
        assertThat(sent(outsiderSocket)).isEmpty();
    }

    // 확인: 이미 접속한 사람이 다시 붙으면 보내지 않고, 끊기면 online false를 한 번 보낸다
    @Test
    void 다시_붙음은_조용히_끊김은_알림() throws Exception {
        long leader = GuildTestAccounts.account(jdbc);
        long member = GuildTestAccounts.account(jdbc);
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "접속끊김" + GuildTestAccounts.suffix(), leader, 2, 30);
        join(member, guildId);
        WebSocketSession leaderSocket = socket(leader);

        events.publishEvent(new RealtimeConnectedEvent(member, serverId.value()));
        events.publishEvent(new RealtimeConnectedEvent(member, serverId.value()));
        events.publishEvent(new RealtimeDisconnectedEvent(member, serverId.value(), 1000));

        List<Boolean> online = sent(leaderSocket).stream()
                .map(message -> JsonPath.<Boolean>read(message, "$.data.online"))
                .toList();
        assertThat(online).containsExactly(true, false);
    }

    // 확인: 미소속 계정의 접속은 아무에게도 보내지 않는다(길드장 혼자인 길드도 같음)
    @Test
    void 미소속과_혼자인_길드는_보내지_않음() throws Exception {
        long loner = GuildTestAccounts.account(jdbc);
        long leader = GuildTestAccounts.account(jdbc);
        GuildTestAccounts.guildWithLeader(jdbc, "혼자" + GuildTestAccounts.suffix(), leader, 1, 30);
        WebSocketSession lonerSocket = socket(loner);
        WebSocketSession leaderSocket = socket(leader);

        events.publishEvent(new RealtimeConnectedEvent(loner, serverId.value()));
        events.publishEvent(new RealtimeConnectedEvent(leader, serverId.value()));

        assertThat(sent(lonerSocket)).isEmpty();
        assertThat(sent(leaderSocket)).isEmpty();
    }

    /** 이 서버 연결 목록에 가짜 WebSocket을 등록한다(연결 이벤트는 내지 않음 — 접속 상태 키는 테스트가 이벤트로 따로 바꿈) */
    private WebSocketSession socket(long accountId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("presence-test-" + accountId);
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

}
