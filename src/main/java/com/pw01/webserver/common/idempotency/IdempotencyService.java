package com.pw01.webserver.common.idempotency;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.config.IdempotencyProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 중복 방지·재전송 받기(지시서 SF-5): 계정 + requestId로 한 번만 실행하고, 같은 요청이 다시 오면 처음 응답을 돌려준다.
 * 상태를 바꾸는 요청(투자 S3, 해금 S4, 우편함 …)의 Service가 직접 부른다(어느 API가 쓰는지 코드에서 보이게).
 *
 * <pre>{@code
 * return idempotency.run(accountId, request.requestId(), request, StatInvestResponse.class, () -> 실제 처리);
 * }</pre>
 *
 * 키 pw01:idem:<계정 ID>:<requestId>, 값 IdempotencyRecord(JSON). 명령 순서는 루트 docs/contracts/redis-keys.md.
 * - 처음: SET NX EX 처리 중 시간 → 실행 → 성공이면 SET XX EX 기억 시간(처음 응답), 예외면 DEL(기억하지 않음, 다시 판정)
 * - 다시 옴: 같은 본문·끝남 → 처음 응답 / 같은 본문·처리 중 → 409 REQUEST_IN_PROGRESS / 다른 본문 → 409 IDEMPOTENCY_KEY_REUSED
 * Redis가 꺼져 있으면 연결 예외가 그대로 나가 503이 된다. 두 번 반영을 막는 마지막 방어는 기능마다 DB에 둔다(투자: 변경 내역 유일 제약).
 * 키에 기능 이름을 넣지 않는다. 다른 API에 같은 requestId를 쓰면 본문 해시가 달라 IDEMPOTENCY_KEY_REUSED가 된다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "pw01:idem:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final IdempotencyProperties properties;

    public <T> T run(Long accountId, String requestId, Object requestBody, Class<T> responseType, Supplier<T> action) {
        String key = key(accountId, requestId);
        String bodyHash = sha256(jsonMapper.writeValueAsString(requestBody));

        // 처리 중 표시를 먼저 잡은 요청만 실행한다. 못 잡았는데 그사이 키가 사라졌으면 한 번 더 시도한다
        for (int attempt = 0; attempt < 2; attempt++) {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, write(IdempotencyRecord.inProgress(bodyHash)),
                    properties.inProgressTtl());
            if (Boolean.TRUE.equals(acquired)) {
                return execute(key, bodyHash, responseType, action);
            }
            String stored = redis.opsForValue().get(key);
            if (stored != null) {
                return replay(key, read(stored), bodyHash, responseType);
            }
        }
        // 잡지도 못하고 값도 없음이 두 번 연속: 다른 요청과 계속 엇갈림. 다시 보내면 된다
        throw requestInProgress();
    }

    private <T> T execute(String key, String bodyHash, Class<T> responseType, Supplier<T> action) {
        T response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            // 거절·실패는 기억하지 않는다. 같은 requestId로 다시 보내면 처음처럼 다시 판정한다
            redis.delete(key);
            throw e;
        }
        Boolean saved = redis.opsForValue().setIfPresent(key,
                write(IdempotencyRecord.done(bodyHash, jsonMapper.writeValueAsString(response))), properties.ttl());
        if (!Boolean.TRUE.equals(saved)) {
            // 처리 중 표시 시간이 먼저 지남. 응답은 그대로 돌려주고, 다시 오면 DB 방어가 막는다
            log.warn("처리 중 표시가 끝난 뒤 응답을 기억하지 못함 key={} type={}", key, responseType.getSimpleName());
        }
        return response;
    }

    private <T> T replay(String key, IdempotencyRecord stored, String bodyHash, Class<T> responseType) {
        if (!stored.bodyHash().equals(bodyHash)) {
            log.warn("같은 requestId에 다른 본문 key={}", key);
            throw new ConflictException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "같은 requestId로 다른 요청을 보낼 수 없습니다.");
        }
        if (stored.state() == IdempotencyRecord.State.IN_PROGRESS) {
            throw requestInProgress();
        }
        log.info("처음 응답을 돌려줌 key={}", key);
        return jsonMapper.readValue(stored.body(), responseType);
    }

    static String key(Long accountId, String requestId) {
        return KEY_PREFIX + accountId + ":" + requestId.toLowerCase(Locale.ROOT);
    }

    private String write(IdempotencyRecord record) {
        return jsonMapper.writeValueAsString(record);
    }

    private IdempotencyRecord read(String value) {
        return jsonMapper.readValue(value, IdempotencyRecord.class);
    }

    private static ConflictException requestInProgress() {
        return new ConflictException(CommonErrorCode.REQUEST_IN_PROGRESS,
                "같은 요청을 처리하고 있습니다. 잠시 뒤 다시 보내 주세요.", true);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

}
