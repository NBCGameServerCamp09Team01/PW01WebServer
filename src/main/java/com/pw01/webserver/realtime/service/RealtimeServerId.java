package com.pw01.webserver.realtime.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 이 서버(프로세스)의 번호. 뜰 때마다 새로 정한다(무작위 8자).
 * 서버 사이 메시지의 origin에 실어 자기가 보낸 것을 알아보고, 로그에서 어느 서버인지 가른다
 * (루트 docs/contracts/redis-keys.md "실시간 서버 사이 전달").
 */
@Component
public class RealtimeServerId {

    private static final Logger log = LoggerFactory.getLogger(RealtimeServerId.class);

    private final String value;

    public RealtimeServerId() {
        this(UUID.randomUUID().toString().substring(0, 8));
    }

    /** 정한 번호로 만든다(시험에서 "다른 서버"를 흉내 낼 때) */
    public static RealtimeServerId of(String value) {
        return new RealtimeServerId(value);
    }

    RealtimeServerId(String value) {
        this.value = value;
        // 컨테이너에서는 HOSTNAME이 컨테이너 ID다. 로그에서 serverId와 서버를 짝지으려고 함께 찍는다
        log.info("[Relay] serverId={} host={}", value, System.getenv().getOrDefault("HOSTNAME", "-"));
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

}
