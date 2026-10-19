package com.pw01.webserver.chat.repository;

import com.pw01.webserver.config.ChatProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 채팅 횟수 제한·도배 쉬기(M23 채팅). 키 셋(pw01:chat-rate·chat-strike·chat-mute:<계정 ID>)을 chat_rate.lua 한 번으로 판단한다.
 * 키·스크립트는 루트 docs/contracts/redis-keys.md "채팅", 값은 pw01.chat.rate.*·mute.*. 서버 메모리에 세지 않는다(서버가 여러 대여도 같은 수).
 * 판단만 돌려주고, 429로 바꾸는 일은 부르는 쪽(ChatService)이 한다.
 * Redis가 꺼져 있으면 DataAccessException을 그대로 던진다(503은 GlobalExceptionHandler).
 * {@code @Repository}가 아니라 {@code @Component}다(RedisLoginFailStore와 같은 까닭 — 예외가 바뀌지 않게).
 */
@Component
public class RedisChatRateLimiter {

    private static final String RATE_PREFIX = "pw01:chat-rate:";
    private static final String STRIKE_PREFIX = "pw01:chat-strike:";
    private static final String MUTE_PREFIX = "pw01:chat-mute:";

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CHAT_RATE = chatRateScript();

    private final StringRedisTemplate redis;
    private final ChatProperties.Rate rate;
    private final ChatProperties.Mute mute;

    public RedisChatRateLimiter(StringRedisTemplate redis, ChatProperties properties) {
        this.redis = redis;
        this.rate = properties.rate();
        this.mute = properties.mute();
    }

    /** 보내기 한 번을 센다. 통과면 그 창의 수가 하나 오르고, 막히면(쉬는 중이면 세지 않음) 남은 시간을 돌려준다 */
    public ChatRateDecision acquire(Long accountId) {
        Objects.requireNonNull(accountId, "accountId");
        String id = accountId.toString();
        List<?> result = redis.execute(CHAT_RATE, List.of(RATE_PREFIX + id, STRIKE_PREFIX + id, MUTE_PREFIX + id),
                Long.toString(rate.window().toMillis()),
                Integer.toString(rate.max()),
                Long.toString(mute.strikeWindow().toMillis()),
                Integer.toString(mute.strikes()),
                Long.toString(mute.duration().toMillis()));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("chat_rate.lua가 {결과, 남은 밀리초}를 돌려주지 않았습니다: " + result);
        }
        long code = ((Number) result.get(0)).longValue();
        long remaining = ((Number) result.get(1)).longValue();
        return switch ((int) code) {
            case 0 -> ChatRateDecision.ALLOW;
            // PTTL이 0 이하(만료 직전·만료 없음)면 창 길이를 남은 시간으로 쓴다(명세)
            case 1 -> new ChatRateDecision(ChatRateDecision.Outcome.RATE_LIMITED,
                    remaining > 0 ? remaining : rate.window().toMillis());
            case 2 -> new ChatRateDecision(ChatRateDecision.Outcome.MUTED,
                    remaining > 0 ? remaining : mute.duration().toMillis());
            default -> throw new IllegalStateException("chat_rate.lua가 모르는 결과를 돌려주었습니다: " + result);
        };
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> chatRateScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/chat_rate.lua"));
        script.setResultType(List.class);
        return script;
    }

}
