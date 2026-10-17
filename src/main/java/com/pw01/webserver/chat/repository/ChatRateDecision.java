package com.pw01.webserver.chat.repository;

/**
 * 채팅 횟수 제한 판단 하나(chat_rate.lua의 돌려준 값).
 *
 * @param outcome         통과 · 횟수 제한 · 쉬는 중
 * @param remainingMillis 막혔을 때 다시 보낼 수 있기까지 남은 밀리초. 통과면 0
 */
public record ChatRateDecision(Outcome outcome, long remainingMillis) {

    public enum Outcome {
        /** 보내도 됨 */
        ALLOWED,
        /** 창 안에서 너무 많이 보냄(429 CHAT_RATE_LIMITED) */
        RATE_LIMITED,
        /** 도배 쉬기 중(429 CHAT_MUTED) */
        MUTED
    }

    public static final ChatRateDecision ALLOW = new ChatRateDecision(Outcome.ALLOWED, 0);

    public boolean allowed() {
        return outcome == Outcome.ALLOWED;
    }

    /** 응답의 retryAfterSeconds: 남은 밀리초를 초로 올림, 최소 1(루트 docs/contracts/chat-api.md "오류") */
    public long retryAfterSeconds() {
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

}
