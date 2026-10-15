package com.pw01.webserver.common.chat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 채팅 한 줄. 채팅·소셜 두 모듈, 실시간 메시지 CHAT_MESSAGE의 data, 내역 응답이 같은 모양을 쓴다
 * (루트 docs/contracts/realtime-api.md "메시지 종류" CHAT_MESSAGE).
 *
 * @param messageId       게임은 모양에 기대지 않는 문자열(같은지 비교·커서로 되돌려 보내기만).
 *                        지금 GUILD = 저장 번호(10진수 문자열) / GLOBAL = UUID(저장 안 함). 전체를 저장하게 되면 GLOBAL도 저장 번호로 바뀔 수 있다
 * @param scope           GLOBAL / GUILD
 * @param guildId         GUILD면 길드 ID, GLOBAL이면 null
 * @param senderAccountId 보낸 사람 계정 ID(문자열 — 공통 규칙)
 * @param senderNickname  보낸 때의 닉네임
 * @param text            채팅 글
 * @param sentAt          서버가 받은 시각(UTC, 밀리초까지)
 */
public record ChatMessage(String messageId, ChatScope scope, String guildId,
                          String senderAccountId, String senderNickname,
                          String text, Instant sentAt) {

    public ChatMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(scope, "scope");
        if (scope == ChatScope.GUILD && guildId == null) {
            throw new IllegalArgumentException("GUILD 채팅에는 guildId가 있어야 합니다");
        }
        if (scope == ChatScope.GLOBAL && guildId != null) {
            throw new IllegalArgumentException("GLOBAL 채팅에는 guildId가 없어야 합니다: " + guildId);
        }
    }

    /**
     * 실시간 메시지 CHAT_MESSAGE의 data로 쓸 칸 묶음. 전체 채팅이면 guildId가 null로 들어간다.
     * 쓰는 법: RealtimeMessage.of(RealtimeMessageType.CHAT_MESSAGE, chatMessage.toRealtimeData())
     */
    public Map<String, Object> toRealtimeData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", messageId);
        data.put("scope", scope.name());
        data.put("guildId", guildId);
        data.put("senderAccountId", senderAccountId);
        data.put("senderNickname", senderNickname);
        data.put("text", text);
        data.put("sentAt", sentAt);
        return data;
    }

}
