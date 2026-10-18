package com.pw01.webserver.stage.service;

import com.pw01.webserver.config.StageContentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 웨이브 정의 안전망: pw01.stage-content.safety-poll마다 출처가 바뀌었는지 스스로 확인한다(HEAD 한 번).
 * 주된 길은 관리 포트 신호 + 서버 사이 알림이고, 이것은 알림을 놓친 서버(Redis 끊김·배포 중 옛 서버)가 따라오게 하는 몫이다.
 * 요청 수는 사용자 수와 상관없이 "서버 수 × 주기"다(5분이면 한 대 하루 288번).
 * 리소스 파일을 쓰는 서버(로컬·시험)에서는 돌지 않는다.
 * 앱 전체의 @EnableScheduling을 켜지 않고 전용 스케줄러를 쓴다(RealtimeHeartbeat와 같은 이유).
 */
@Component
public class StageContentSafetyPoll implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StageContentSafetyPoll.class);

    private final StageContentReloader reloader;
    private final Duration interval;
    private ThreadPoolTaskScheduler scheduler;

    public StageContentSafetyPoll(StageContentReloader reloader, StageContentProperties properties) {
        this.reloader = reloader;
        this.interval = properties.safetyPoll();
    }

    @Override
    public void afterPropertiesSet() {
        if (!reloader.reloadable()) {
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stage-content-poll-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::check, Instant.now().plus(interval), interval);
        log.info("[Content] 안전망 주기 확인 켬 interval={}", interval);
    }

    /** 한 번 확인. 예외가 스케줄러 밖으로 나가면 다음 확인이 멈추므로 모두 잡는다 */
    void check() {
        try {
            reloader.reload("poll");
        } catch (RuntimeException e) {
            log.warn("[Content] 안전망 주기 확인 중 예외 — 지금 정의 유지", e);
        }
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

}
