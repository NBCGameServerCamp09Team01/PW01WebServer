package com.pw01.webserver.common.chat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 시험용 가짜 GuildChatHistory(메모리). 소셜 구현이 아직 없을 때 채팅 모듈이 이것으로 시험하고,
 * 소셜 구현은 ChatContractTest와 같은 기대(최신부터·커서는 그 줄 빼고·보관 기간 밖은 안 줌)를 맞춘다.
 */
public class InMemoryGuildChatHistory implements GuildChatHistory {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Duration retention;
    private final Supplier<Instant> now;

    /**
     * @param retention 보관 기간(이보다 옛것은 돌려주지 않음)
     * @param now       지금 시각(시험에서 시간을 옮기려고 받는다)
     */
    public InMemoryGuildChatHistory(Duration retention, Supplier<Instant> now) {
        this.retention = retention;
        this.now = now;
    }

    @Override
    public synchronized ChatMessage append(String guildId, Long senderAccountId, String senderNickname, String text) {
        ChatMessage message = new ChatMessage(String.valueOf(sequence.incrementAndGet()), ChatScope.GUILD, guildId,
                String.valueOf(senderAccountId), senderNickname, text, now.get().truncatedTo(ChronoUnit.MILLIS));
        messages.add(message);
        return message;
    }

    @Override
    public synchronized ChatHistoryPage loadLatest(String guildId, String before, int size) {
        Instant oldest = now.get().minus(retention);
        long beforeId = before == null ? Long.MAX_VALUE : Long.parseLong(before);
        List<ChatMessage> page = messages.reversed().stream()
                .filter(message -> message.guildId().equals(guildId))
                .filter(message -> Long.parseLong(message.messageId()) < beforeId)
                .filter(message -> !message.sentAt().isBefore(oldest))
                .limit(size + 1L)
                .toList();
        if (page.size() > size) {
            List<ChatMessage> trimmed = page.subList(0, size);
            return new ChatHistoryPage(trimmed, trimmed.getLast().messageId());
        }
        return new ChatHistoryPage(page, null);
    }

}
