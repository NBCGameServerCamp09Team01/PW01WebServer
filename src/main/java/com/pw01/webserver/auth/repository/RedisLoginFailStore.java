package com.pw01.webserver.auth.repository;

import com.pw01.webserver.config.AuthProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * LoginFailStore의 Redis 구현(pw01:login-fail:<아이디>). 첫 실패부터 window 안에 maxAttempts번 틀리면 그때부터 lock 동안 잠긴다.
 * 아이디는 대소문자를 구분하므로 입력 그대로 키에 쓴다. 키·스크립트는 루트 docs/contracts/redis-keys.md v1, 값은 pw01.auth.login-fail.*.
 * 형식(영문 대소문자·숫자 4~20자) 검사는 AuthService.login이 먼저 한다. 여기서는 키 길이를 묶으려고 한 번 더 보고, 형식 밖이면 키를 만들지 않고 IllegalArgumentException.
 * Redis가 꺼져 있으면 DataAccessException을 그대로 던진다(503은 GlobalExceptionHandler).
 * {@code @Repository}가 아니라 {@code @Component}다. {@code @Repository}면 위 IllegalArgumentException까지 DataAccessException으로 바뀐다.
 */
@Component
public class RedisLoginFailStore implements LoginFailStore {

    private static final String KEY_PREFIX = "pw01:login-fail:";
    private static final Pattern LOGIN_ID = Pattern.compile("[A-Za-z0-9]{4,20}");

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> RECORD_FAILURE = recordFailureScript();

    private final StringRedisTemplate redis;
    private final AuthProperties.LoginFail settings;

    public RedisLoginFailStore(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.settings = properties.loginFail();
    }

    @Override
    public long lockRemaining(String loginId) {
        String key = key(loginId);
        String count = redis.opsForValue().get(key);
        if (count == null || Long.parseLong(count) < settings.maxAttempts()) {
            return 0;
        }
        Long ttl = redis.getExpire(key);
        // 읽는 사이에 키가 사라지면 -2가 온다(잠김이 막 풀림)
        return ttl == null ? 0 : Math.max(ttl, 0);
    }

    @Override
    public LoginFailResult recordFailure(String loginId) {
        List<?> result = redis.execute(RECORD_FAILURE, List.of(key(loginId)),
                Integer.toString(settings.maxAttempts()),
                Long.toString(settings.window().toSeconds()),
                Long.toString(settings.lock().toSeconds()));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("login_fail.lua가 {횟수, 남은 잠김 초}를 돌려주지 않았습니다: " + result);
        }
        long count = ((Number) result.get(0)).longValue();
        long lockRemainingSeconds = Math.max(((Number) result.get(1)).longValue(), 0);
        return new LoginFailResult(count, lockRemainingSeconds);
    }

    @Override
    public void clear(String loginId) {
        redis.delete(key(loginId));
    }

    private static String key(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw new IllegalArgumentException("아이디 형식이 아닙니다(영문 대소문자·숫자 4~20자). 형식 검사를 먼저 해야 합니다");
        }
        return KEY_PREFIX + loginId;
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> recordFailureScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/login_fail.lua"));
        script.setResultType(List.class);
        return script;
    }

}
