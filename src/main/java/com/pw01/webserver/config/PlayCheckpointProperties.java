package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 플레이 체크포인트 설정 값(pw01.play-checkpoint.*). 기본값은 application.properties에 있다. 틀린 값이면 서버가 뜨지 않는다.
 * 뜻은 루트 docs/contracts/stage-play-api.md "C1·C2. 체크포인트", redis-keys.md "플레이 체크포인트".
 *
 * @param maxStateSize 체크포인트 판 안 값(state)을 JSON으로 썼을 때의 최대 크기. 넘으면 C1이 400
 */
@ConfigurationProperties("pw01.play-checkpoint")
public record PlayCheckpointProperties(DataSize maxStateSize) {

    public PlayCheckpointProperties {
        if (maxStateSize == null || maxStateSize.toBytes() <= 0) {
            throw new IllegalArgumentException("pw01.play-checkpoint.max-state-size는 0보다 커야 합니다: " + maxStateSize);
        }
    }

}
