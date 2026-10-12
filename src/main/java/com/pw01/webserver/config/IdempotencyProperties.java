package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 중복 방지(SF-5) 설정 값(pw01.idempotency.*). 기본값은 application.properties에 있다. 틀린 값이면 서버가 뜨지 않는다.
 * Redis EXPIRE가 초 단위라 둘 다 1초 이상의 초 단위여야 한다.
 *
 * @param ttl           처음 응답을 기억하는 시간
 * @param inProgressTtl 처리 중 표시 시간. 요청 하나가 끝나는 시간보다 넉넉해야 한다(서버가 죽어도 이 시간 뒤 풀림)
 */
@ConfigurationProperties("pw01.idempotency")
public record IdempotencyProperties(Duration ttl, Duration inProgressTtl) {

    public IdempotencyProperties {
        requireSeconds("pw01.idempotency.ttl", ttl);
        requireSeconds("pw01.idempotency.in-progress-ttl", inProgressTtl);
    }

    private static void requireSeconds(String name, Duration value) {
        if (value == null || value.toSeconds() < 1 || value.toNanosPart() != 0) {
            throw new IllegalArgumentException(name + "는 1초 이상의 초 단위여야 합니다: " + value);
        }
    }

}
