package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 접속 상태 설정을 켜고, 값끼리의 관계를 뜰 때 확인한다.
 * 키 수명이 Ping 간격보다 짧으면 Pong 사이에 키가 사라져 접속한 사람이 "접속 안 함"으로 깜빡인다 → 서버가 뜨지 않게 한다.
 */
@Configuration
@EnableConfigurationProperties(PresenceProperties.class)
public class PresenceConfig {

    public PresenceConfig(PresenceProperties presence, RealtimeProperties realtime) {
        checkTtl(presence, realtime);
    }

    static void checkTtl(PresenceProperties presence, RealtimeProperties realtime) {
        if (presence.ttl().compareTo(realtime.pingInterval()) <= 0) {
            throw new IllegalStateException("pw01.presence.ttl(" + presence.ttl()
                    + ")은 pw01.realtime.ping-interval(" + realtime.pingInterval() + ")보다 길어야 합니다");
        }
    }

}
