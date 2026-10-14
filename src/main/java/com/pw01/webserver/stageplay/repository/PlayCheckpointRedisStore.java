package com.pw01.webserver.stageplay.repository;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 플레이 체크포인트의 Redis 저장소. 키 pw01:play-checkpoint:<stagePlayId>, 값은 StoredPlayCheckpoint JSON.
 * 키·스크립트(src/main/resources/redis)·명령 순서는 루트 docs/contracts/redis-keys.md "플레이 체크포인트(v1.4)".
 * Redis가 꺼져 있으면 DataAccessException을 그대로 던진다(503은 GlobalExceptionHandler).
 * {@code @Repository}가 아니라 {@code @Component}다(RedisSessionStore와 같은 이유: 이 클래스가 던진 예외까지 바뀌지 않게).
 */
@Component
public class PlayCheckpointRedisStore {

    private static final String KEY_PREFIX = "pw01:play-checkpoint:";

    private static final DefaultRedisScript<Long> PUT = putScript();

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public PlayCheckpointRedisStore(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 덮어쓴다. 저장된 체크포인트의 clearedWave보다 작으면 저장하지 않고 false(비교와 저장을 한 번에).
     * 만료는 ttl을 초로 올림하고 최소 1초다(플레이 마감까지 남은 시간).
     */
    public boolean put(String stagePlayId, StoredPlayCheckpoint checkpoint, Duration ttl) {
        long ttlSeconds = Math.max(1L, (ttl.toMillis() + 999) / 1000);
        Long result = redis.execute(PUT, List.of(key(stagePlayId)),
                String.valueOf(checkpoint.clearedWave()), jsonMapper.writeValueAsString(checkpoint), String.valueOf(ttlSeconds));
        if (result == null) {
            throw new IllegalStateException("play_checkpoint_put.lua가 결과를 돌려주지 않았습니다");
        }
        return result == 1L;
    }

    public Optional<StoredPlayCheckpoint> find(String stagePlayId) {
        String json = redis.opsForValue().get(key(stagePlayId));
        return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, StoredPlayCheckpoint.class));
    }

    public void delete(String stagePlayId) {
        redis.delete(key(stagePlayId));
    }

    private static String key(String stagePlayId) {
        return KEY_PREFIX + stagePlayId;
    }

    private static DefaultRedisScript<Long> putScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/play_checkpoint_put.lua"));
        script.setResultType(Long.class);
        return script;
    }

}
