package com.pw01.webserver.realtime.service;

import com.pw01.webserver.config.RealtimeRelayProperties;
import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.dto.RealtimeRelayMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 서버 사이 메시지 받기 단위 테스트(Docker 불필요). 채널·JSON을 직접 넣어 이 서버 연결에 무엇이 가는지 본다.
 * 실패하면 RealtimeRelayListener의 거르기(자기 것·판·종류·길드 맞춤)와 모르는 칸 무시(redis-keys.md "실시간 서버 사이 전달")를 본다.
 */
class RealtimeRelayListenerTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";
    private static final String GLOBAL = "pw01:realtime-relay:global";
    private static final String TARGET = "pw01:realtime-relay:target";
    private static final String GUILD_7 = "pw01:realtime-relay:guild:7";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private RealtimeConnectionRegistry registry;
    private RealtimeHeartbeat heartbeat;
    private RealtimeEventPublisher events;
    private RealtimeRelayListener listener;

    @BeforeEach
    void setUp() {
        registry = new RealtimeConnectionRegistry();
        heartbeat = mock(RealtimeHeartbeat.class);
        events = mock(RealtimeEventPublisher.class);
        RealtimeRelayProperties properties = RealtimeNotifierTest.relayProperties();
        listener = new RealtimeRelayListener(new RealtimeLocalDelivery(registry, jsonMapper), registry, heartbeat,
                properties, new RealtimeServerId("thisserv"), events, jsonMapper);
    }

    // 확인: 다른 서버가 길드 7 채널에 올린 메시지는 목록 가운데 이 서버에 붙은 사람에게만 같은 id로 간다
    @Test
    void 길드_채널_메시지는_목록의_이_서버_사람에게만() throws Exception {
        WebSocketSession member = connect(12L);
        WebSocketSession outsider = connect(99L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "hi"));

        listener.handle(GUILD_7, json(RealtimeRelayMessage.deliver("otherser", "7", List.of("12", "57"), message)));

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(member).sendMessage(sent.capture());
        assertThat(sent.getValue().getPayload()).contains(message.id());
        verify(outsider, never()).sendMessage(any());
    }

    // 확인: 채널의 길드와 메시지의 guildId가 다르면 버린다(다른 길드 사람에게 가지 않게)
    @Test
    void 채널과_길드가_다르면_버린다() throws Exception {
        WebSocketSession member = connect(12L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of());

        listener.handle(GUILD_7, json(RealtimeRelayMessage.deliver("otherser", "8", List.of("12"), message)));

        verify(member, never()).sendMessage(any());
    }

    // 확인: 전체 채널 메시지는 이 서버 연결 모두에게
    @Test
    void 전체_채널_메시지는_모두에게() throws Exception {
        WebSocketSession a = connect(1L);
        WebSocketSession b = connect(2L);

        listener.handle(GLOBAL, json(RealtimeRelayMessage.deliver("otherser", null, null,
                RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of()))));

        verify(a).sendMessage(any(TextMessage.class));
        verify(b).sendMessage(any(TextMessage.class));
    }

    // 확인: 자기가 보낸 메시지(origin이 이 서버)는 버린다 — 이 서버 연결에는 보낼 때 이미 보냈다
    @Test
    void 자기가_보낸_메시지는_버린다() throws Exception {
        WebSocketSession a = connect(1L);

        listener.handle(GLOBAL, json(RealtimeRelayMessage.deliver("thisserv", null, null,
                RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of()))));

        verify(a, never()).sendMessage(any());
    }

    // 확인: 세션 다시 확인은 그 계정 연결이 이 서버에 있을 때 Pong과 같은 판정(revalidate)을 부른다
    @Test
    void 세션_다시_확인은_이_서버_연결만_판정한다() {
        connect(1L);

        listener.handle(TARGET, json(RealtimeRelayMessage.sessionCheck("otherser", List.of("1", "2"))));

        verify(heartbeat).revalidate(registry.find(1L).orElseThrow());
    }

    // 확인: 새 서버가 칸을 더해도(모르는 칸) 옛 서버는 읽는다. 모르는 판(v=2)·깨진 JSON·모르는 종류는 버리고 예외를 밖으로 던지지 않는다
    @Test
    void 모르는_칸은_무시하고_나쁜_메시지는_버린다() throws Exception {
        WebSocketSession a = connect(1L);
        String withExtra = "{\"v\":1,\"kind\":\"DELIVER\",\"origin\":\"otherser\",\"accountIds\":[\"1\"],\"future\":true,"
                + "\"message\":{\"type\":\"CHAT_MESSAGE\",\"id\":\"m1\",\"sentAt\":\"2026-10-17T05:00:00.123Z\",\"data\":{},\"later\":1}}";

        listener.handle(TARGET, withExtra.getBytes(StandardCharsets.UTF_8));
        verify(a).sendMessage(any(TextMessage.class));

        assertThatCode(() -> {
            listener.handle(TARGET, "{\"v\":2,\"kind\":\"DELIVER\",\"origin\":\"x\"}".getBytes(StandardCharsets.UTF_8));
            listener.handle(TARGET, "not json".getBytes(StandardCharsets.UTF_8));
            listener.handle(TARGET, "{\"v\":1,\"kind\":\"WHAT\",\"origin\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        }).doesNotThrowAnyException();
    }

    // 확인(판 12): 다른 서버의 CONTENT_CHANGED는 이벤트로만 넘기고(게임 연결에는 아무것도 보내지 않음), 자기 것은 버린다.
    // 실패하면 handle의 kind 분기와 origin 거르기를 의심한다
    @Test
    void 정의_바뀜_알림은_이벤트로만() throws Exception {
        WebSocketSession socket = connect(12L);

        listener.handle(GLOBAL, json(RealtimeRelayMessage.contentChanged("otherser")));
        listener.handle(GLOBAL, json(RealtimeRelayMessage.contentChanged("thisserv")));

        verify(events).contentChanged("otherser");
        verify(events, never()).contentChanged("thisserv");
        verify(socket, never()).sendMessage(any(TextMessage.class));
    }

    private byte[] json(RealtimeRelayMessage relayMessage) {
        return jsonMapper.writeValueAsBytes(relayMessage);
    }

    private WebSocketSession connect(Long accountId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("s" + accountId);
        registry.register(new RealtimeConnection(socket, accountId, TOKEN));
        return socket;
    }

}
