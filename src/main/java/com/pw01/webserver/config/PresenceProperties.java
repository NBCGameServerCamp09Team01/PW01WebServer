package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 접속 상태 설정 값(pw01.presence.*). 기본값은 application.properties에 있다.
 * 값의 뜻은 루트 docs/contracts/redis-keys.md "접속 상태" 절이 기준이다.
 * Ping 간격과의 관계(ttl > pw01.realtime.ping-interval)는 PresenceConfig가 뜰 때 확인한다.
 *
 * @param ttl 접속 상태 키 수명. 연결·Pong마다 다시 채운다. 서버가 갑자기 꺼지면 이만큼 뒤에 "접속 안 함"이 된다
 */
@ConfigurationProperties("pw01.presence")
public record PresenceProperties(Duration ttl) {

    public PresenceProperties {
        if (ttl == null || ttl.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("pw01.presence.ttl은 1초 이상이어야 합니다: " + ttl);
        }
    }

}
