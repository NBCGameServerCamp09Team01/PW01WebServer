package com.pw01.webserver.chat.repository;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.chat.repository.ChatRateDecision.Outcome;
import com.pw01.webserver.config.ChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 횟수 제한·도배 쉬기: 루트 docs/contracts/redis-keys.md "채팅"의 키 셋과 chat_rate.lua가 명세대로 움직이는지 실제 Redis로 확인한다.
 * 기본 설정(10초 5번, 1분 안에 경고 3번 → 60초)으로 본다. 새 창은 시간을 기다리지 않고 창 키를 지워 흉내 낸다.
 * 실패하면 chat_rate.lua의 인자 순서·경고를 세는 조건(n == max + 1), pw01.chat.rate.*·mute.* 설정을 먼저 의심한다.
 * 테스트끼리 Redis를 함께 쓰므로 계정 ID를 서로 다르게 쓴다(93001~).
 */
@IntegrationTest
class RedisChatRateLimiterTest {

    @Autowired
    RedisChatRateLimiter limiter;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    ChatProperties properties;

    // 확인: 창 안 5번까지 통과, 6번째는 횟수 제한이고 남은 시간은 창 길이 안. 창 키 TTL도 창 길이 안
    @Test
    void 창_안에서_다섯_번까지() {
        List<ChatRateDecision> results = acquireTimes(93001L, max() + 1);

        assertThat(results.subList(0, max())).allMatch(ChatRateDecision::allowed);
        ChatRateDecision sixth = results.getLast();
        assertThat(sixth.outcome()).isEqualTo(Outcome.RATE_LIMITED);
        assertThat(sixth.remainingMillis()).isBetween(1L, windowMillis());
        assertThat(redis.getExpire("pw01:chat-rate:93001")).isBetween(1L, properties.rate().window().toSeconds());
    }

    // 확인: 한 창에서 몇 번을 더 두드려도 경고는 1이다(실수로 빠르게 친 사람은 바로 쉬지 않음). 경고 키 TTL은 경고 창 안
    @Test
    void 경고는_창마다_한_번() {
        List<ChatRateDecision> results = acquireTimes(93002L, max() + 20);

        assertThat(results.subList(max(), results.size())).allMatch(d -> d.outcome() == Outcome.RATE_LIMITED);
        assertThat(redis.opsForValue().get("pw01:chat-strike:93002")).isEqualTo("1");
        assertThat(redis.getExpire("pw01:chat-strike:93002")).isBetween(1L, properties.mute().strikeWindow().toSeconds());
        assertThat(redis.hasKey("pw01:chat-mute:93002")).isFalse();
    }

    // 확인: 서로 다른 창 셋에서 제한에 걸리면 세 번째에 쉬기 시작(남은 시간 = 쉬는 시간 전체), 경고 키는 지워짐.
    // 쉬는 동안은 첫 요청도 막히고 창 수를 세지 않는다. 쉬기 키가 사라지면 다시 보낼 수 있다
    @Test
    void 세_창에서_걸리면_쉬기() {
        Long account = 93003L;
        acquireTimes(account, max() + 1);
        redis.delete("pw01:chat-rate:" + account);
        acquireTimes(account, max() + 1);
        redis.delete("pw01:chat-rate:" + account);
        ChatRateDecision third = acquireTimes(account, max() + 1).getLast();

        assertThat(third.outcome()).isEqualTo(Outcome.MUTED);
        assertThat(third.remainingMillis()).isEqualTo(properties.mute().duration().toMillis());
        assertThat(redis.hasKey("pw01:chat-strike:" + account)).isFalse();

        redis.delete("pw01:chat-rate:" + account);
        ChatRateDecision whileMuted = limiter.acquire(account);
        assertThat(whileMuted.outcome()).isEqualTo(Outcome.MUTED);
        assertThat(whileMuted.remainingMillis()).isBetween(1L, properties.mute().duration().toMillis());
        assertThat(redis.hasKey("pw01:chat-rate:" + account)).isFalse();

        redis.delete("pw01:chat-mute:" + account);
        assertThat(limiter.acquire(account).allowed()).isTrue();
    }

    // 확인: 같은 계정이 동시에 10번 보내도 정확히 5번만 통과한다(스크립트 한 번이 읽기-판단-쓰기 — 과제 "Lua로 동시 요청에도 횟수 제한")
    @Test
    void 동시에_보내도_다섯_번만() throws Exception {
        List<ChatRateDecision> results = runTogether(93004L, 10);

        assertThat(results).filteredOn(ChatRateDecision::allowed).hasSize(max());
        assertThat(results).filteredOn(d -> d.outcome() == Outcome.RATE_LIMITED).hasSize(10 - max());
        assertThat(redis.opsForValue().get("pw01:chat-strike:93004")).isEqualTo("1");
    }

    // 확인: 쉬는 중에 동시에 보내도 모두 쉬는 중으로 막힌다
    @Test
    void 쉬는_중_동시_요청은_모두_막힘() throws Exception {
        redis.opsForValue().set("pw01:chat-mute:93005", "1", properties.mute().duration());

        assertThat(runTogether(93005L, 8)).allMatch(d -> d.outcome() == Outcome.MUTED);
    }

    // 확인: 다른 계정은 따로 센다
    @Test
    void 계정마다_따로() {
        acquireTimes(93006L, max() + 1);

        assertThat(limiter.acquire(93007L).allowed()).isTrue();
    }

    // 확인: retryAfterSeconds는 남은 밀리초를 초로 올림, 최소 1(명세 chat-api.md "오류")
    @Test
    void 다시_보낼_수_있는_초는_올림() {
        assertThat(new ChatRateDecision(Outcome.RATE_LIMITED, 1500).retryAfterSeconds()).isEqualTo(2);
        assertThat(new ChatRateDecision(Outcome.RATE_LIMITED, 1000).retryAfterSeconds()).isEqualTo(1);
        assertThat(new ChatRateDecision(Outcome.RATE_LIMITED, 1).retryAfterSeconds()).isEqualTo(1);
        assertThat(new ChatRateDecision(Outcome.MUTED, 60_000).retryAfterSeconds()).isEqualTo(60);
    }

    private List<ChatRateDecision> acquireTimes(Long accountId, int times) {
        List<ChatRateDecision> results = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            results.add(limiter.acquire(accountId));
        }
        return results;
    }

    /** 같은 계정 보내기를 같이 출발시켜 결과를 모은다(GuildConcurrencyTest와 같은 틀) */
    private List<ChatRateDecision> runTogether(Long accountId, int count) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatRateDecision>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            for (int i = 0; i < count; i++) {
                Callable<ChatRateDecision> task = () -> {
                    start.await();
                    return limiter.acquire(accountId);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<ChatRateDecision> results = new ArrayList<>();
            for (Future<ChatRateDecision> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private int max() {
        return properties.rate().max();
    }

    private long windowMillis() {
        return properties.rate().window().toMillis();
    }

}
