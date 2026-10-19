package com.pw01.webserver.common.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 이 서버(프로세스)의 번호. 뜰 때마다 새로 정한다(무작위 8자).
 * 서버 사이 메시지의 origin에 실어 자기가 보낸 것을 알아보고(실시간), 접속 상태 키의 값으로 쓰고(접속 상태),
 * 응답 헤더 X-Server-Id·로그로 어느 서버가 요청을 받았는지 가른다(ServerIdFilter, 루트 docs/flows/ops-health.md).
 * 여러 기능이 같이 쓰는 프로세스 전체의 값이라 common에 둔다(판 12 W5, 전에는 realtime/service/ServerId).
 * 뜰 때 로그 문구 "[Relay] serverId=… host=…"는 두 대 점검 절차(H2-04)가 찾으므로 바꾸지 않는다.
 */
@Component
public class ServerId {

    private static final Logger log = LoggerFactory.getLogger(ServerId.class);

    private final String value;

    public ServerId() {
        this(UUID.randomUUID().toString().substring(0, 8));
    }

    /** 정한 번호로 만든다(시험에서 "다른 서버"를 흉내 낼 때) */
    public static ServerId of(String value) {
        return new ServerId(value);
    }

    ServerId(String value) {
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
