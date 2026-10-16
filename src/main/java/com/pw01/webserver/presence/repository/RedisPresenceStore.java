package com.pw01.webserver.presence.repository;

import com.pw01.webserver.config.PresenceProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 접속 상태 키 pw01:presence:&lt;계정&gt; = 연결이 붙은 서버 번호(TTL). 루트 docs/contracts/redis-keys.md "접속 상태" v1.6.
 * Redis 오류는 그대로 던진다(DataAccessException). 연결을 끊지 않을지·빈 값으로 볼지는 PresenceService가 정한다.
 * {@code @Repository}가 아니라 {@code @Component}다(RedisSessionStore와 같은 이유).
 */
@Component
public class RedisPresenceStore {

    private static final String KEY_PREFIX = "pw01:presence:";
    private static final DefaultRedisScript<Long> RELEASE = script("redis/presence_release.lua");

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisPresenceStore(StringRedisTemplate redis, PresenceProperties properties) {
        this.redis = redis;
        this.ttl = properties.ttl();
    }

    /** 접속함(연결·Pong): 이 서버 번호로 덮어쓰고 수명을 다시 채운다 */
    public void markOnline(Long accountId, String serverId) {
        redis.opsForValue().set(key(accountId), serverId, ttl);
    }

    /**
     * 끊김: 값이 이 서버 번호일 때만 지운다. 그사이 다른 서버로 다시 붙었으면 그 서버의 값을 남긴다.
     *
     * @return 지웠으면 true
     */
    public boolean release(Long accountId, String serverId) {
        Long deleted = redis.execute(RELEASE, List.of(key(accountId)), serverId);
        return deleted != null && deleted > 0;
    }

    /** 이 가운데 접속한 계정(MGET 한 번). 비어 있으면 Redis를 부르지 않는다 */
    public Set<Long> onlineOf(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = List.copyOf(accountIds);
        List<String> values = redis.opsForValue().multiGet(ids.stream().map(RedisPresenceStore::key).toList());
        Set<Long> online = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            if (values != null && values.get(i) != null) {
                online.add(ids.get(i));
            }
        }
        return online;
    }

    static String key(Long accountId) {
        return KEY_PREFIX + accountId;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

}
