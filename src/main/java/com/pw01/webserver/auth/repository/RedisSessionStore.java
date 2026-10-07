package com.pw01.webserver.auth.repository;

import com.pw01.webserver.config.AuthProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * SessionStore의 Redis 구현. 키 둘을 쓴다: pw01:session:<토큰 해시> → 계정 ID, pw01:account-session:<계정 ID> → 지금 토큰 해시.
 * 키·스크립트(src/main/resources/redis)·명령 순서는 루트 docs/contracts/redis-keys.md v1, 세션 수명은 pw01.auth.session.ttl.
 * 토큰 원문은 create만 돌려주고 Redis·로그에는 해시만 남긴다. 토큰 모양 검사는 AuthService.authenticate가 먼저 한다.
 * Redis가 꺼져 있으면 RedisConnectionFailureException(DataAccessException)을 그대로 던진다(503은 GlobalExceptionHandler).
 * {@code @Repository}가 아니라 {@code @Component}다. {@code @Repository}면 이 클래스가 던진 예외까지 DataAccessException으로 바뀐다.
 */
@Component
public class RedisSessionStore implements SessionStore {

    private static final String SESSION_KEY_PREFIX = "pw01:session:";
    private static final String ACCOUNT_SESSION_KEY_PREFIX = "pw01:account-session:";
    private static final int TOKEN_BYTES = 32;

    private static final DefaultRedisScript<String> LOGIN = script("redis/session_login.lua", String.class);
    private static final DefaultRedisScript<Long> EXTEND = script("redis/session_extend.lua", Long.class);
    private static final DefaultRedisScript<Long> LOGOUT = script("redis/session_logout.lua", Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RedisSessionStore(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.ttl = properties.session().ttl();
    }

    @Override
    public IssuedSession create(Long accountId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);

        redis.execute(LOGIN, List.of(sessionKey(hash), accountSessionKey(accountId)),
                accountId.toString(), hash, ttlSeconds());
        return new IssuedSession(token, expiresAt());
    }

    @Override
    public SessionCheck touch(String token) {
        String hash = hash(token);
        String accountId = redis.opsForValue().get(sessionKey(hash));
        if (accountId == null) {
            return SessionCheck.notFound();
        }

        Long result = redis.execute(EXTEND, List.of(sessionKey(hash), accountSessionKey(accountId)), hash, ttlSeconds());
        if (result == null) {
            throw new IllegalStateException("session_extend.lua가 결과를 돌려주지 않았습니다");
        }
        return switch (result.intValue()) {
            case 1 -> SessionCheck.ok(Long.valueOf(accountId), expiresAt());
            case 2 -> SessionCheck.replaced();
            default -> SessionCheck.notFound();
        };
    }

    @Override
    public void delete(String token, Long accountId) {
        String hash = hash(token);
        redis.execute(LOGOUT, List.of(sessionKey(hash), accountSessionKey(accountId)), hash);
    }

    private Instant expiresAt() {
        return Instant.now().plus(ttl).truncatedTo(ChronoUnit.MILLIS);
    }

    private String ttlSeconds() {
        return Long.toString(ttl.toSeconds());
    }

    private static String sessionKey(String hash) {
        return SESSION_KEY_PREFIX + hash;
    }

    private static String accountSessionKey(Object accountId) {
        return ACCOUNT_SESSION_KEY_PREFIX + accountId;
    }

    /** SHA-256, 소문자 16진수 64자 */
    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    private static <T> DefaultRedisScript<T> script(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }

}
