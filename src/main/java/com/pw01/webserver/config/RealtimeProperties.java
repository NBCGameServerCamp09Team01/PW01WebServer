package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 실시간 연결 설정 값(pw01.realtime.*). 기본값은 application.properties에 있다.
 * 값의 뜻은 루트 docs/contracts/realtime-api.md "Heartbeat"·"설정 값" 절이 기준이다.
 * 세션 수명과의 관계(Ping 간격 × 허용 Pong 없음 < 세션 수명)는 RealtimeHeartbeat가 뜰 때 확인한다.
 *
 * @param pingInterval   연결마다 Ping을 보내는 간격. Pong을 받을 때마다 세션을 다시 건다
 * @param maxMissedPongs 이만큼 연속으로 Pong이 없으면 연결을 닫는다(종료 코드 4003)
 */
@ConfigurationProperties("pw01.realtime")
public record RealtimeProperties(Duration pingInterval, int maxMissedPongs) {

    public RealtimeProperties {
        if (pingInterval == null || pingInterval.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("pw01.realtime.ping-interval은 1초 이상이어야 합니다: " + pingInterval);
        }
        if (maxMissedPongs < 1) {
            throw new IllegalArgumentException("pw01.realtime.max-missed-pongs는 1 이상이어야 합니다: " + maxMissedPongs);
        }
    }

}
