package com.pw01.webserver.common.chat;

import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 채팅·소셜 두 모듈 사이 약속(common.chat) 단위 테스트(Docker 불필요). 두 팀원이 같은 기대로 구현·시험하도록 기준을 둔다.
 * 실패하면 ChatMessage의 칸 규칙, 커서 규칙(최신부터·그 줄 빼고·보관 기간), 저장 안 함 구현을 본다.
 */
class ChatContractTest {

    private static final Instant NOW = Instant.parse("2026-10-17T05:00:00Z");

    // 확인: CHAT_MESSAGE data — 칸 일곱, 전체 채팅이면 guildId가 null로 들어가고 JSON에도 null로 나간다(realtime-api.md 메시지 종류)
    @Test
    void 채팅_한_줄은_실시간_data로_바뀐다() {
        ChatMessage global = new NoStoreGlobalChatHistory().append(12L, "용사", "안녕");

        RealtimeMessage message = RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, global.toRealtimeData());
        String json = JsonMapper.builder().build().writeValueAsString(message);

        assertThat(message.data()).containsKeys("messageId", "scope", "guildId", "senderAccountId", "senderNickname", "text", "sentAt");
        assertThat(json).contains("\"scope\":\"GLOBAL\"").contains("\"guildId\":null").contains("\"senderAccountId\":\"12\"");
    }

    // 확인: 범위와 guildId가 어긋나면 만들 수 없다(길드 채팅인데 길드가 없음 / 전체 채팅인데 길드가 있음)
    @Test
    void 범위와_길드가_어긋나면_만들_수_없다() {
        assertThatThrownBy(() -> new ChatMessage("1", ChatScope.GUILD, null, "1", "a", "t", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatMessage("1", ChatScope.GLOBAL, "7", "1", "a", "t", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 확인: 전체 채팅은 지금 저장하지 않는다 — 불러오기는 빈 묶음, stores()=false
    @Test
    void 전체_채팅은_저장하지_않는다() {
        NoStoreGlobalChatHistory history = new NoStoreGlobalChatHistory();
        history.append(1L, "a", "hi");

        assertThat(history.loadLatest(null, 30).messages()).isEmpty();
        assertThat(history.stores()).isFalse();
    }

    // 확인: 길드 내역 커서 — 최신부터 size개(새것 → 옛것), nextCursor로 다음 묶음, 커서 줄은 빼고, 다른 길드 것은 안 섞임
    @Test
    void 길드_내역은_최신부터_커서로_불러온다() {
        InMemoryGuildChatHistory history = new InMemoryGuildChatHistory(Duration.ofDays(3), () -> NOW);
        for (int i = 1; i <= 5; i++) {
            history.append("7", 1L, "a", "m" + i);
        }
        history.append("8", 2L, "b", "other guild");

        ChatHistoryPage first = history.loadLatest("7", null, 2);
        ChatHistoryPage second = history.loadLatest("7", first.nextCursor(), 2);
        ChatHistoryPage last = history.loadLatest("7", second.nextCursor(), 2);

        assertThat(first.messages()).extracting(ChatMessage::text).containsExactly("m5", "m4");
        assertThat(second.messages()).extracting(ChatMessage::text).containsExactly("m3", "m2");
        assertThat(last.messages()).extracting(ChatMessage::text).containsExactly("m1");
        assertThat(last.nextCursor()).isNull();
    }

    // 확인: 보관 기간(3일, 채팅 지시서 2판) 밖의 줄은 돌려주지 않는다
    @Test
    void 보관_기간_밖은_돌려주지_않는다() {
        AtomicReference<Instant> clock = new AtomicReference<>(NOW.minus(Duration.ofDays(4)));
        InMemoryGuildChatHistory history = new InMemoryGuildChatHistory(Duration.ofDays(3), clock::get);
        history.append("7", 1L, "a", "too old");
        assertThat(history.loadLatest("7", null, 30).messages()).hasSize(1);

        clock.set(NOW);
        history.append("7", 1L, "a", "fresh");

        assertThat(history.loadLatest("7", null, 30).messages()).extracting(ChatMessage::text).containsExactly("fresh");
    }

    // 확인: 길드 채팅 한 바퀴 — 저장(GuildChatHistory) → 길드원 목록(GuildDirectory) → 그 길드 채널로 보내기(sendToGuild).
    // 채팅 모듈의 서비스가 이 순서로 부르면 된다는 견본
    @Test
    void 길드_채팅_한_바퀴() {
        GuildDirectory directory = new InMemoryGuildDirectory().join("7", 1L, 2L).join("8", 3L);
        GuildChatHistory history = new InMemoryGuildChatHistory(Duration.ofDays(7), () -> NOW);
        RealtimeNotifier notifier = mock(RealtimeNotifier.class);

        String guildId = directory.guildIdOf(1L).orElseThrow();
        ChatMessage saved = history.append(guildId, 1L, "용사", "안녕");
        notifier.sendToGuild(guildId, directory.memberIds(guildId),
                RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, saved.toRealtimeData()));

        verify(notifier).sendToGuild(eq("7"), eq(List.of(1L, 2L)),
                argThat(message -> "1".equals(message.data().get("messageId")) && "GUILD".equals(message.data().get("scope"))));
    }

}
