package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 스테이지 플레이·결과 설정 값(pw01.stage-play.*). 기본값은 application.properties에 있다. 틀린 값이면 서버가 뜨지 않는다.
 *
 * @param validity      스테이지 플레이 유효 시간(시작 → 결과 마감)
 * @param playTimeMax   결과의 플레이 시간 상한
 * @param playTimeSlack 서버가 잰 경과 시간에 더해 주는 여유(게임 시계·전송 지연)
 * @param saveAttempts  낙관적 락 충돌 때 결과 저장을 다시 하는 총 횟수(1 이상)
 */
@ConfigurationProperties("pw01.stage-play")
public record StagePlayProperties(Duration validity, Duration playTimeMax, Duration playTimeSlack, int saveAttempts) {

    public StagePlayProperties {
        requirePositive("pw01.stage-play.validity", validity);
        requirePositive("pw01.stage-play.play-time-max", playTimeMax);
        if (playTimeSlack == null || playTimeSlack.isNegative()) {
            throw new IllegalArgumentException("pw01.stage-play.play-time-slack는 0 이상이어야 합니다: " + playTimeSlack);
        }
        if (saveAttempts < 1) {
            throw new IllegalArgumentException("pw01.stage-play.save-attempts는 1 이상이어야 합니다: " + saveAttempts);
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + "는 0보다 커야 합니다: " + value);
        }
    }

}
