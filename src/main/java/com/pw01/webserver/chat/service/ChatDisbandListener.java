package com.pw01.webserver.chat.service;

import com.pw01.webserver.common.chat.GuildDisbandedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 길드가 해체되면 그 길드 채팅 줄을 지운다(M23, 루트 docs/flows/chat.md ⑦).
 * 소셜이 해체 트랜잭션 안에서 낸 GuildDisbandedEvent를 커밋 뒤에 받는다. 해체가 롤백되면 받지 않는다(줄이 남음 = 길드도 남음).
 * 지우기는 GuildChatHistoryImpl.deleteGuild(새 트랜잭션, 묶음 반복). 실패해도 해체 응답은 바꾸지 않고 경고만 남긴다 —
 * 그 길드를 읽을 사람이 없고(길드 ID는 다시 쓰지 않음) 남은 줄은 보관 지우기(GuildChatCleanupJob)가 3일 안에 지운다.
 */
@Component
@RequiredArgsConstructor
public class ChatDisbandListener {

    private static final Logger log = LoggerFactory.getLogger(ChatDisbandListener.class);

    private final GuildChatHistoryImpl guildChatHistory;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGuildDisbanded(GuildDisbandedEvent event) {
        try {
            int deleted = guildChatHistory.deleteGuild(event.guildId());
            log.info("해체된 길드 채팅 지움 guildId={} rows={}", event.guildId(), deleted);
        } catch (RuntimeException e) {
            log.warn("해체된 길드 채팅을 지우지 못함(보관 지우기가 나중에 지움) guildId={} cause={}",
                    event.guildId(), e.getMessage());
        }
    }

}
