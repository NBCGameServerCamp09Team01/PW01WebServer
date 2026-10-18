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
 * 웨이브 정의를 뒤에서 다시 읽는 전용 스레드 하나.
 * <ul>
 *   <li>안전망: pw01.stage-content.safety-poll마다 출처가 바뀌었는지 스스로 확인한다(HEAD 한 번). 주된 길은 관리 포트 신호 +
 *       서버 사이 알림이고, 이것은 알림을 놓친 서버(Redis 끊김·배포 중 옛 서버)가 따라오게 하는 몫이다</li>
 *   <li>알림 받은 다시 읽기(reloadSoon): 서버 사이 알림은 실시간 받기 스레드 하나에서 오므로, S3 읽기를 거기서 하지 않고 이 스레드로 넘긴다
 *       (채팅 같은 다른 서버 사이 메시지가 밀리지 않게)</li>
 * </ul>
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
        scheduler.scheduleWithFixedDelay(() -> run("poll"), Instant.now().plus(interval), interval);
        log.info("[Content] 안전망 주기 확인 켬 interval={}", interval);
    }

    /**
     * 이 스레드에서 곧 한 번 다시 읽는다(부른 스레드는 기다리지 않음). 리소스 파일을 쓰는 서버면 아무것도 하지 않는다.
     *
     * @param trigger 로그에 남길 까닭(relay 등)
     */
    public void reloadSoon(String trigger) {
        if (scheduler != null) {
            scheduler.execute(() -> run(trigger));
        }
    }

    /** 한 번 확인. 예외가 스케줄러 밖으로 나가면 다음 확인이 멈추므로 모두 잡는다 */
    void run(String trigger) {
        try {
            reloader.reload(trigger);
        } catch (RuntimeException e) {
            log.warn("[Content] 정의 다시 읽기 중 예외 trigger={} — 지금 정의 유지", trigger, e);
        }
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

}
