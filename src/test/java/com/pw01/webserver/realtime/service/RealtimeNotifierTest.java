package com.pw01.webserver.realtime.service;

import com.pw01.webserver.config.RealtimeRelayProperties;
import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.repository.RealtimeRelayPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 보내기 입구 넷 단위 테스트(Docker 불필요). 이 서버 연결에는 바로 보내고, 같은 메시지를 맞는 채널로 넘기는지 본다.
 * 실패하면 RealtimeNotifier의 채널 고르기(지정·길드·전체)와 한도 검사(redis-keys.md "실시간 서버 사이 전달")를 본다.
 */
class RealtimeNotifierTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJK-_0123";

    private RealtimeConnectionRegistry registry;
    private RealtimeRelayPublisher publisher;
    private RealtimeNotifier notifier;

    /** 시험용 기본 설정(application.properties와 같은 값) */
    static RealtimeRelayProperties relayProperties() {
        return new RealtimeRelayProperties(true, "pw01:realtime-relay:global", "pw01:realtime-relay:guild:",
                "pw01:realtime-relay:target", 500, 8192);
    }

    @BeforeEach
    void setUp() {
        registry = new RealtimeConnectionRegistry();
        publisher = mock(RealtimeRelayPublisher.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        notifier = new RealtimeNotifier(new RealtimeLocalDelivery(registry, jsonMapper), publisher, relayProperties(), jsonMapper);
    }

    // 확인: 한 사람에게 — 이 서버에 붙어 있으면 바로 보내고, 지정 채널로도 넘긴다(그 사람이 다른 서버에도 있을 수 있음)
    @Test
    void 한_사람에게_보내면_바로_보내고_지정_채널로_넘긴다() throws Exception {
        WebSocketSession socket = connect(1L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "hi"));

        notifier.send(1L, message);

        verify(socket).sendMessage(any(TextMessage.class));
        verify(publisher).publishToAccounts(List.of(1L), message);
    }

    // 확인: 길드에게 — 길드원 가운데 이 서버 사람에게만 보내고, 그 길드의 채널로 넘긴다(목록의 중복·null은 뺀다)
    @Test
    void 길드에게_보내면_길드_채널로_넘긴다() throws Exception {
        WebSocketSession member = connect(1L);
        WebSocketSession outsider = connect(9L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "guild"));

        notifier.sendToGuild("7", Arrays.asList(1L, 2L, 1L, null), message);

        verify(member).sendMessage(any(TextMessage.class));
        verify(outsider, never()).sendMessage(any());
        verify(publisher).publishToGuild("7", List.of(1L, 2L), message);
    }

    // 확인: 전체에게 — 이 서버 연결 모두에게 보내고 전체 채널로 넘긴다
    @Test
    void 전체에게_보내면_전체_채널로_넘긴다() throws Exception {
        WebSocketSession a = connect(1L);
        WebSocketSession b = connect(2L);
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "all"));

        notifier.sendToAll(message);

        verify(a).sendMessage(any(TextMessage.class));
        verify(b).sendMessage(any(TextMessage.class));
        verify(publisher).publishToAll(message);
    }

    // 확인: 계정 수 한도(500)를 넘으면 아무 데도 보내지 않는다(경고 로그만)
    @Test
    void 대상이_한도를_넘으면_보내지_않는다() {
        List<Long> tooMany = LongStream.rangeClosed(1, 501).boxed().toList();

        notifier.sendToAccounts(tooMany, RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of()));

        verifyNoInteractions(publisher);
    }

    // 확인: 메시지 크기 한도(8KB)를 넘으면 보내지 않는다
    @Test
    void 메시지가_너무_크면_보내지_않는다() throws Exception {
        WebSocketSession socket = connect(1L);

        notifier.sendToAll(RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of("text", "가".repeat(5000))));

        verify(socket, never()).sendMessage(any());
        verifyNoInteractions(publisher);
    }

    // 확인: 길드 ID에 패턴 문자·':'가 섞이면 부르는 쪽 실수로 보고 막는다(다른 길드 채널을 건드리지 않게)
    @Test
    void 길드_ID_모양이_틀리면_예외() {
        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, Map.of());

        assertThatThrownBy(() -> notifier.sendToGuild("7*", List.of(1L), message)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notifier.sendToGuild("a:b", List.of(1L), message)).isInstanceOf(IllegalArgumentException.class);
        verify(publisher, never()).publishToGuild(anyString(), any(), any());
    }

    // 확인: 다른 곳 로그인 → 다른 서버들에 세션 다시 확인을 넘긴다
    @Test
    void 세션_다시_확인을_넘긴다() {
        notifier.requestSessionCheck(1L);

        verify(publisher).publishSessionCheck(1L);
    }

    private WebSocketSession connect(Long accountId) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getId()).thenReturn("s" + accountId);
        registry.register(new RealtimeConnection(socket, accountId, TOKEN));
        return socket;
    }

}
