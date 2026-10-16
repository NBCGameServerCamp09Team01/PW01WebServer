package com.pw01.webserver.chat.controller;

import com.jayway.jsonpath.JsonPath;
import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.guild.GuildTestAccounts;
import com.pw01.webserver.realtime.service.RealtimeConnection;
import com.pw01.webserver.realtime.service.RealtimeConnectionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 채팅 API(루트 docs/contracts/chat-api.md CH1·CH2·CH3)를 HTTP로 확인한다: 판정 순서, 한 번만 반영, 429, 실시간 CHAT_MESSAGE 받는 사람, 내역 커서.
 * 실시간은 이 서버 연결 목록(RealtimeConnectionRegistry)에 가짜 WebSocket을 등록해 실제로 보내진 JSON을 읽는다(GuildNotifierTest와 같은 틀).
 * 길드 소속은 SQL로 준비한다(GuildTestAccounts). 횟수 제한 수는 Redis pw01:chat-rate:<계정 ID>를 직접 읽는다.
 * 실패하면 ChatService의 판정 순서(글 규칙 → 멱등 → chat_rate.lua → 길드), ChatSendKey(scope 포함), 커밋 뒤 sendToGuild를 먼저 의심한다.
 */
@IntegrationTest
class ChatApiTest {

    private static final String PASSWORD = "password123!";
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    RealtimeConnectionRegistry registry;

    private final List<RealtimeConnection> connections = new ArrayList<>();

    @AfterEach
    void disconnect() {
        connections.forEach(registry::unregister);
        connections.clear();
    }

    // 확인: CH1 — 글 앞뒤 공백을 자르고, 응답 data가 채팅 한 줄 모양(guildId 칸은 null로 있음),
    // 접속한 사람 모두(보낸 사람 포함)에게 같은 messageId의 CHAT_MESSAGE가 감
    @Test
    void 전체_보내기() throws Exception {
        Session sender = signupAndLogin();
        Session other = signupAndLogin();
        WebSocketSession senderSocket = connect(sender.accountId());
        WebSocketSession otherSocket = connect(other.accountId());

        String body = send(sender, "/chats/global", uuid(), "  안녕하세요 👋  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.data", hasKey("guildId")))
                .andExpect(jsonPath("$.data.guildId").value(nullValue()))
                .andExpect(jsonPath("$.data.senderAccountId").value(String.valueOf(sender.accountId())))
                .andExpect(jsonPath("$.data.senderNickname").value(sender.nickname()))
                .andExpect(jsonPath("$.data.text").value("안녕하세요 👋"))
                .andExpect(jsonPath("$.data.sentAt").isString())
                .andReturn().getResponse().getContentAsString();
        String messageId = JsonPath.read(body, "$.data.messageId");

        for (WebSocketSession socket : List.of(senderSocket, otherSocket)) {
            Map<String, Object> message = only(socket);
            assertThat(message.get("type")).isEqualTo("CHAT_MESSAGE");
            assertThat(JsonPath.<String>read(message, "$.data.messageId")).isEqualTo(messageId);
            assertThat(JsonPath.<String>read(message, "$.data.scope")).isEqualTo("GLOBAL");
            assertThat(JsonPath.<String>read(message, "$.data.text")).isEqualTo("안녕하세요 👋");
        }
    }

    // 확인: 같은 requestId·같은 본문을 다시 보내면 처음 응답 그대로(같은 messageId), 실시간은 한 번만, 횟수도 한 번만 셈
    @Test
    void 같은_요청을_다시_보내면_처음_응답() throws Exception {
        Session sender = signupAndLogin();
        WebSocketSession socket = connect(sender.accountId());
        String key = uuid();

        String first = send(sender, "/chats/global", key, "한 번만").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = send(sender, "/chats/global", key, "한 번만").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(second, "$.data.messageId"))
                .isEqualTo(JsonPath.<String>read(first, "$.data.messageId"));
        assertThat(sent(socket)).hasSize(1);
        assertThat(rateCount(sender)).isEqualTo("1");
    }

    // 확인: 같은 requestId를 다른 글이나 다른 곳(전체 → 길드)에 쓰면 409 IDEMPOTENCY_KEY_REUSED(ChatSendKey에 scope가 있음)
    @Test
    void 같은_requestId를_다른_곳에_쓰면_409() throws Exception {
        Session sender = signupAndLogin();
        GuildTestAccounts.guildWithLeader(jdbc, "채팅키" + GuildTestAccounts.suffix(), sender.accountId(), 1, 30);
        String key = uuid();

        send(sender, "/chats/global", key, "같은 글").andExpect(status().isOk());
        expectCode(send(sender, "/chats/guild", key, "같은 글"), 409, "IDEMPOTENCY_KEY_REUSED");
        expectCode(send(sender, "/chats/global", key, "다른 글"), 409, "IDEMPOTENCY_KEY_REUSED");
    }

    // 확인: CH2 — 저장(guild_chat_message 행)하고 그 길드원(보낸 사람 포함)에게만 보냄. 길드 밖 사람은 받지 않음
    @Test
    void 길드_보내기() throws Exception {
        Session leader = signupAndLogin();
        Session member = signupAndLogin();
        Session outsider = signupAndLogin();
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "채팅길드" + GuildTestAccounts.suffix(),
                leader.accountId(), 2, 30);
        joinDirect(member.accountId(), guildId);
        List<WebSocketSession> receivers = List.of(connect(leader.accountId()), connect(member.accountId()));
        WebSocketSession outsiderSocket = connect(outsider.accountId());

        String body = send(member, "/chats/guild", uuid(), "길드 안녕")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("GUILD"))
                .andExpect(jsonPath("$.data.guildId").value(String.valueOf(guildId)))
                .andExpect(jsonPath("$.data.senderNickname").value(member.nickname()))
                .andReturn().getResponse().getContentAsString();
        String messageId = JsonPath.read(body, "$.data.messageId");

        assertThat(jdbc.queryForObject("""
                SELECT text FROM guild_chat_message WHERE guild_chat_message_id = ? AND guild_id = ? AND sender_account_id = ?""",
                String.class, Long.parseLong(messageId), guildId, member.accountId())).isEqualTo("길드 안녕");
        for (WebSocketSession socket : receivers) {
            Map<String, Object> message = only(socket);
            assertThat(message.get("type")).isEqualTo("CHAT_MESSAGE");
            assertThat(JsonPath.<String>read(message, "$.data.messageId")).isEqualTo(messageId);
            assertThat(JsonPath.<String>read(message, "$.data.guildId")).isEqualTo(String.valueOf(guildId));
        }
        assertThat(sent(outsiderSocket)).isEmpty();
    }

    // 확인: 길드 없이 CH2 → 403 GUILD_NOT_MEMBER, 그래도 횟수는 셈(명세 "횟수 제한은 길드 확인보다 먼저"). CH3도 403
    @Test
    void 길드가_없으면_403() throws Exception {
        Session loner = signupAndLogin();

        expectCode(send(loner, "/chats/guild", uuid(), "아무도 없나요"), 403, "GUILD_NOT_MEMBER");
        assertThat(rateCount(loner)).isEqualTo("1");
        expectCode(mockMvc.perform(get("/chats/guild").header(HttpHeaders.AUTHORIZATION, loner.bearer())),
                403, "GUILD_NOT_MEMBER");
    }

    // 확인: 형식은 VALIDATION_FAILED·INVALID_REQUEST_BODY, 글 규칙은 CHAT_TEXT_INVALID(field = text). 400은 횟수를 세지 않음
    @Test
    void 잘못된_요청은_400() throws Exception {
        Session sender = signupAndLogin();

        for (String text : List.of("   ", "a".repeat(101), "줄\n바꿈")) {
            send(sender, "/chats/global", uuid(), text)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHAT_TEXT_INVALID"))
                    .andExpect(jsonPath("$.errors[0].field").value("text"));
        }
        expectCode(postJson(sender, "/chats/global", """
                {"requestId": "%s"}""".formatted(uuid())), 400, "VALIDATION_FAILED");
        expectCode(postJson(sender, "/chats/global", """
                {"requestId": "not-a-uuid", "text": "안녕"}"""), 400, "VALIDATION_FAILED");
        expectCode(postJson(sender, "/chats/global", """
                {"requestId": "%s", "text": "안녕", "guildId": "7"}""".formatted(uuid())), 400, "INVALID_REQUEST_BODY");
        assertThat(rateCount(sender)).isNull();
    }

    // 확인: 10초에 5번까지 200, 6번째 429 CHAT_RATE_LIMITED(본문 retryAfterSeconds = Retry-After 헤더, 1~10초).
    // 429는 기억하지 않아, 창이 끝난 뒤 같은 requestId로 다시 보내면 200
    @Test
    void 너무_빨리_보내면_429() throws Exception {
        Session sender = signupAndLogin();
        for (int i = 0; i < 5; i++) {
            send(sender, "/chats/global", uuid(), "빠르게 " + i).andExpect(status().isOk());
        }
        String key = uuid();

        String body = send(sender, "/chats/global", key, "여섯 번째")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CHAT_RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn().getResponse().getContentAsString();
        int retryAfter = JsonPath.read(body, "$.retryAfterSeconds");
        assertThat(retryAfter).isBetween(1, 10);

        redis.delete("pw01:chat-rate:" + sender.accountId());
        send(sender, "/chats/global", key, "여섯 번째").andExpect(status().isOk());
    }

    // 확인: 도배 쉬는 중이면 전체·길드 모두 429 CHAT_MUTED(남은 쉬기 시간), 창 수는 세지 않음
    @Test
    void 쉬는_중이면_429_muted() throws Exception {
        Session sender = signupAndLogin();
        GuildTestAccounts.guildWithLeader(jdbc, "쉬기" + GuildTestAccounts.suffix(), sender.accountId(), 1, 30);
        redis.opsForValue().set("pw01:chat-mute:" + sender.accountId(), "1", Duration.ofSeconds(60));

        for (String uri : List.of("/chats/global", "/chats/guild")) {
            String body = send(sender, uri, uuid(), "쉬는 중")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("CHAT_MUTED"))
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andReturn().getResponse().getContentAsString();
            int retryAfter = JsonPath.read(body, "$.retryAfterSeconds");
            assertThat(retryAfter).isBetween(1, 60);
        }
        assertThat(rateCount(sender)).isNull();
    }

    // 확인: CH3 — 기본 10줄, 새것 → 옛것, nextCursor로 이어 받기(겹침 없음), 끝이면 nextCursor null.
    // 다른 길드·3일 지난 줄은 나오지 않음
    @Test
    void 길드_내역() throws Exception {
        Session reader = signupAndLogin();
        long guildId = GuildTestAccounts.guildWithLeader(jdbc, "내역" + GuildTestAccounts.suffix(),
                reader.accountId(), 1, 30);
        long otherGuild = GuildTestAccounts.guildWithLeader(jdbc, "남의" + GuildTestAccounts.suffix(),
                GuildTestAccounts.account(jdbc), 1, 30);
        insertMessage(guildId, "옛날", Duration.ofDays(4));
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(insertMessage(guildId, "줄 " + i, Duration.ZERO));
        }
        insertMessage(otherGuild, "남의 글", Duration.ZERO);

        String first = history(reader, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages", hasSize(10)))
                .andExpect(jsonPath("$.data.messages[0].messageId").value(String.valueOf(ids.get(11))))
                .andExpect(jsonPath("$.data.messages[0].scope").value("GUILD"))
                .andExpect(jsonPath("$.data.messages[0].guildId").value(String.valueOf(guildId)))
                .andExpect(jsonPath("$.data.nextCursor").value(String.valueOf(ids.get(2))))
                .andReturn().getResponse().getContentAsString();

        history(reader, JsonPath.read(first, "$.data.nextCursor"), 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[*].text", contains("줄 1", "줄 0")))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    // 확인: CH3 쿼리 규칙 — size 1~50, before는 10진수 문자열. 밖이면 400 VALIDATION_FAILED(field 이름)
    @Test
    void 내역_쿼리가_틀리면_400() throws Exception {
        Session reader = signupAndLogin();
        GuildTestAccounts.guildWithLeader(jdbc, "쿼리" + GuildTestAccounts.suffix(), reader.accountId(), 1, 30);

        for (int size : List.of(0, 51)) {
            history(reader, null, size)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("size"));
        }
        for (String before : List.of("abc", "-1", "١٢", "99999999999999999999", "")) {
            history(reader, before, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("before"));
        }
        history(reader, null, 50).andExpect(status().isOk());
    }

    private ResultActions send(Session session, String uri, String requestId, String text) throws Exception {
        return mockMvc.perform(post(uri)
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "%s", "text": "%s"}""".formatted(requestId, text.replace("\n", "\\n"))));
    }

    private ResultActions postJson(Session session, String uri, String json) throws Exception {
        return mockMvc.perform(post(uri)
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions history(Session session, String before, Integer size) throws Exception {
        var request = get("/chats/guild").header(HttpHeaders.AUTHORIZATION, session.bearer());
        if (before != null) {
            request.param("before", before);
        }
        if (size != null) {
            request.param("size", String.valueOf(size));
        }
        return mockMvc.perform(request);
    }

    private void expectCode(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private String rateCount(Session session) {
        return redis.opsForValue().get("pw01:chat-rate:" + session.accountId());
    }

    private void joinDirect(long accountId, long guildId) {
        jdbc.update("""
                INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
                VALUES (?, ?, 'MEMBER', NOW(3), 0, NOW(3), NOW(3))
                ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id), role = 'MEMBER', joined_at = NOW(3)""",
                accountId, guildId);
    }

    /** 길드 채팅 한 줄을 SQL로(age만큼 옛날 시각). 저장 번호를 돌려준다 */
    private long insertMessage(long guildId, String text, Duration age) {
        jdbc.update("""
                INSERT INTO guild_chat_message (guild_id, sender_account_id, sender_nickname, text, created_at, updated_at)
                VALUES (?, 1, '보낸이', ?, NOW(3) - INTERVAL ? SECOND, NOW(3) - INTERVAL ? SECOND)""",
                guildId, text, age.toSeconds(), age.toSeconds());
        return jdbc.queryForObject("SELECT MAX(guild_chat_message_id) FROM guild_chat_message WHERE guild_id = ?",
                Long.class, guildId);
    }

    private WebSocketSession connect(long accountId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("chat-test-" + accountId);
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

    private Session signupAndLogin() throws Exception {
        String loginId = "Cht" + UUID.randomUUID().toString().substring(0, 8);
        String nickname = "채팅" + loginId.substring(3, 9);
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
        return new Session("Bearer " + JsonPath.read(body, "$.data.accessToken"), accountId, nickname);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private record Session(String bearer, Long accountId, String nickname) {
    }

}
