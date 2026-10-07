package com.pw01.webserver.auth.repository;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Redis 세션 저장소: 루트 docs/contracts/redis-keys.md의 세션 키 둘과 session_*.lua가 명세대로 움직이는지 실제 Redis로 확인한다.
 * 실패하면 키 이름, Lua 스크립트(src/main/resources/redis), pw01.auth.session.ttl 설정, StringRedisTemplate 직렬화를 먼저 의심한다.
 * 테스트끼리 Redis를 함께 쓰므로 계정 ID를 서로 다르게 쓴다(9001~, 실제 가입 계정과 겹치지 않게). 시간은 기다리지 않고 키 TTL을 줄여 흉내 낸다.
 */
@IntegrationTest
class RedisSessionStoreTest {

    @Autowired
    SessionStore sessions;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    AuthProperties properties;

    /** 새 세션: 토큰은 43자 URL-safe, Redis 키·값에 원문이 없고, 두 키의 TTL은 설정한 세션 수명이다 */
    @Test
    void createStoresHashedKeysWithTtl() {
        IssuedSession issued = sessions.create(9001L);

        assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.expiresAt()).isCloseTo(Instant.now().plus(ttl()), within(Duration.ofSeconds(5)));

        String hash = redis.opsForValue().get("pw01:account-session:9001");
        assertThat(hash).hasSize(64).isNotEqualTo(issued.token());
        assertThat(redis.opsForValue().get("pw01:session:" + hash)).isEqualTo("9001");
        assertThat(redis.keys("pw01:*" + issued.token() + "*")).isEmpty();
        assertThat(redis.getExpire("pw01:session:" + hash)).isBetween(ttl().toSeconds() - 2, ttl().toSeconds());
        assertThat(redis.getExpire("pw01:account-session:9001")).isBetween(ttl().toSeconds() - 2, ttl().toSeconds());
    }

    /** 요청마다 연장: 남은 시간을 줄여 둔 뒤 touch하면 두 키가 다시 세션 수명이 되고, 계정 ID와 늘어난 만료 시각을 돌려준다 */
    @Test
    void touchExtendsBothKeys() {
        IssuedSession issued = sessions.create(9002L);
        String hash = redis.opsForValue().get("pw01:account-session:9002");
        redis.expire("pw01:session:" + hash, Duration.ofSeconds(5));
        redis.expire("pw01:account-session:9002", Duration.ofSeconds(5));

        SessionCheck check = sessions.touch(issued.token());

        assertThat(check.status()).isEqualTo(SessionCheck.Status.OK);
        assertThat(check.accountId()).isEqualTo(9002L);
        assertThat(check.expiresAt()).isCloseTo(Instant.now().plus(ttl()), within(Duration.ofSeconds(5)));
        assertThat(redis.getExpire("pw01:session:" + hash)).isGreaterThan(ttl().toSeconds() - 3);
        assertThat(redis.getExpire("pw01:account-session:9002")).isGreaterThan(ttl().toSeconds() - 3);
    }

    /** Redis에 없는 토큰은 NOT_FOUND */
    @Test
    void unknownTokenIsNotFound() {
        assertThat(sessions.touch("A".repeat(43)).status()).isEqualTo(SessionCheck.Status.NOT_FOUND);
    }

    /** 다른 곳에서 로그인: 이전 토큰은 REPLACED, 새 토큰은 OK(새 로그인이 이김) */
    @Test
    void newLoginReplacesPreviousSession() {
        IssuedSession first = sessions.create(9004L);
        IssuedSession second = sessions.create(9004L);

        assertThat(sessions.touch(first.token()).status()).isEqualTo(SessionCheck.Status.REPLACED);
        assertThat(sessions.touch(second.token()).status()).isEqualTo(SessionCheck.Status.OK);
    }

    /** 로그아웃 경합: 다른 곳에서 로그인한 뒤 이전 기기가 로그아웃해도 새 기기의 세션은 그대로다 */
    @Test
    void logoutOfReplacedSessionKeepsNewSession() {
        IssuedSession first = sessions.create(9005L);
        IssuedSession second = sessions.create(9005L);

        sessions.delete(first.token(), 9005L);

        assertThat(sessions.touch(first.token()).status()).isEqualTo(SessionCheck.Status.NOT_FOUND);
        assertThat(sessions.touch(second.token()).status()).isEqualTo(SessionCheck.Status.OK);
    }

    /** 계정 세션 키만 지움(제재를 손으로 걸 때): 그 계정의 토큰은 REPLACED가 아니라 NOT_FOUND */
    @Test
    void accountKeyRemovedMakesSessionNotFound() {
        IssuedSession issued = sessions.create(9006L);

        redis.delete("pw01:account-session:9006");

        assertThat(sessions.touch(issued.token()).status()).isEqualTo(SessionCheck.Status.NOT_FOUND);
    }

    /** 로그아웃: 두 키가 지워지고 같은 토큰은 NOT_FOUND */
    @Test
    void logoutDeletesBothKeys() {
        IssuedSession issued = sessions.create(9007L);
        String hash = redis.opsForValue().get("pw01:account-session:9007");

        sessions.delete(issued.token(), 9007L);

        assertThat(redis.hasKey("pw01:account-session:9007")).isFalse();
        assertThat(redis.hasKey("pw01:session:" + hash)).isFalse();
        assertThat(sessions.touch(issued.token()).status()).isEqualTo(SessionCheck.Status.NOT_FOUND);
    }

    private Duration ttl() {
        return properties.session().ttl();
    }

}
