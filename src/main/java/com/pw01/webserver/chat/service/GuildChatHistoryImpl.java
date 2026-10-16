package com.pw01.webserver.chat.service;

import com.pw01.webserver.chat.entity.GuildChatMessage;
import com.pw01.webserver.chat.repository.GuildChatMessageRepository;
import com.pw01.webserver.common.chat.ChatHistoryPage;
import com.pw01.webserver.common.chat.ChatMessage;
import com.pw01.webserver.common.chat.ChatScope;
import com.pw01.webserver.common.chat.GuildChatHistory;
import com.pw01.webserver.config.ChatProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 두 모듈 약속 GuildChatHistory(common/chat)의 채팅 구현(M23, 채팅 지시서 2판 — 길드 채팅 저장·내역은 채팅 모듈).
 * guild_chat_message 표(V11)에 저장하고 최신부터 커서로 읽는다. 보관 기간(pw01.chat.guild.retention, 3일)보다 오래된 줄은 읽지 않는다.
 * 약속의 길드 ID는 문자열이고 실제 값은 guild.guild_id의 10진수 문자열이다(GuildDirectoryImpl과 같은 규칙).
 * 숫자가 아닌 길드 ID: 불러오기는 "그런 길드 없음"으로 빈 묶음, 저장은 부르는 쪽 실수라 IllegalArgumentException.
 */
@Service
public class GuildChatHistoryImpl implements GuildChatHistory {

    private final GuildChatMessageRepository repository;
    private final Duration retention;

    public GuildChatHistoryImpl(GuildChatMessageRepository repository, ChatProperties properties) {
        this.repository = repository;
        this.retention = properties.guild().retention();
    }

    /** 부르는 쪽 트랜잭션 안에서 부른다(약속). 커밋 뒤에 실시간으로 보내는 일은 부르는 쪽이 한다 */
    @Override
    public ChatMessage append(String guildId, Long senderAccountId, String senderNickname, String text) {
        Long id = parseId(guildId)
                .orElseThrow(() -> new IllegalArgumentException("길드 ID는 10진수 문자열이어야 합니다: " + guildId));
        GuildChatMessage saved = repository.save(GuildChatMessage.of(id, senderAccountId, senderNickname, text));
        return toChatMessage(saved);
    }

    /**
     * @param before 이 번호보다 옛것부터(그 줄은 빼고). null이면 가장 최신부터. 숫자가 아니면 IllegalArgumentException(부르는 쪽이 400으로 거름)
     */
    @Override
    public ChatHistoryPage loadLatest(String guildId, String before, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
        }
        Optional<Long> id = parseId(guildId);
        if (id.isEmpty()) {
            return ChatHistoryPage.empty();
        }
        long beforeId = before == null ? Long.MAX_VALUE
                : parseId(before).orElseThrow(() -> new IllegalArgumentException("before는 10진수 문자열이어야 합니다: " + before));
        Instant since = Instant.now().minus(retention);

        // 하나 더 읽어 다음 묶음이 있는지 안다
        List<GuildChatMessage> rows = repository.findByGuildIdAndIdLessThanAndCreatedAtGreaterThanEqualOrderByIdDesc(
                id.get(), beforeId, since, Limit.of(size + 1));
        boolean hasMore = rows.size() > size;
        List<GuildChatMessage> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? String.valueOf(page.getLast().getId()) : null;
        return new ChatHistoryPage(page.stream().map(GuildChatHistoryImpl::toChatMessage).toList(), nextCursor);
    }

    private static ChatMessage toChatMessage(GuildChatMessage row) {
        return new ChatMessage(String.valueOf(row.getId()), ChatScope.GUILD, String.valueOf(row.getGuildId()),
                String.valueOf(row.getSenderAccountId()), row.getSenderNickname(), row.getText(), row.sentAt());
    }

    /** ASCII 10진수 문자열만 받는다(앞에 +·공백·다른 문자의 숫자 없음). 아니면 빈 값 */
    private static Optional<Long> parseId(String value) {
        if (value == null || value.isEmpty() || value.length() > 19
                || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

}
