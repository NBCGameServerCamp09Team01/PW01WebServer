package com.pw01.webserver.common.chat;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 전체 채팅을 저장하지 않는 구현(지금 쓰는 것, M23 10/15 결정). 번호(UUID)와 시각만 매겨 돌려준다.
 * 나중에 저장 구현을 만들면 그 클래스에 @Primary를 붙인다(또는 이 클래스를 지운다). 부르는 쪽은 바뀌지 않는다.
 */
@Component
public class NoStoreGlobalChatHistory implements GlobalChatHistory {

    @Override
    public ChatMessage append(Long senderAccountId, String senderNickname, String text) {
        return new ChatMessage(UUID.randomUUID().toString(), ChatScope.GLOBAL, null,
                String.valueOf(senderAccountId), senderNickname, text, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public ChatHistoryPage loadLatest(String before, int size) {
        return ChatHistoryPage.empty();
    }

    @Override
    public boolean stores() {
        return false;
    }

}
