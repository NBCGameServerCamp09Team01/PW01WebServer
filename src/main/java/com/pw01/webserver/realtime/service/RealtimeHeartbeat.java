package com.pw01.webserver.realtime.service;

import com.pw01.webserver.auth.service.AuthService;
import com.pw01.webserver.common.error.UnauthorizedException;
import com.pw01.webserver.config.AuthProperties;
import com.pw01.webserver.config.RealtimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.PingMessage;

import java.time.Duration;
import java.time.Instant;

/**
 * 실시간 연결의 heartbeat(루트 docs/contracts/realtime-api.md "Heartbeat").
 * ping-interval마다 연결마다 Ping을 보내고, Pong을 받으면 세션을 다시 건다(HTTP 인증과 같은 AuthService 판정).
 * 연속 max-missed-pongs번 Pong이 없으면 4003으로 닫는다.
 * <p>
 * 앱 전체의 @EnableScheduling을 켜지 않고 전용 스케줄러를 쓴다. 다른 기능의 스케줄 설정에 영향을 주지 않기 위해서다
 * (@EnableWebSocket이 만드는 기본 스케줄러가 앱의 @Scheduled를 가로채는 문제도 피한다).
 */
@Service
public class RealtimeHeartbeat implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHeartbeat.class);

    private final RealtimeConnectionRegistry registry;
    private final RealtimeNotifier notifier;
    private final AuthService authService;
    private final RealtimeProperties properties;
    private final Duration sessionTtl;
    private ThreadPoolTaskScheduler scheduler;

    public RealtimeHeartbeat(RealtimeConnectionRegistry registry, RealtimeNotifier notifier, AuthService authService,
                             RealtimeProperties properties, AuthProperties authProperties) {
        this.registry = registry;
        this.notifier = notifier;
        this.authService = authService;
        this.properties = properties;
        this.sessionTtl = authProperties.session().ttl();
    }

    @Override
    public void afterPropertiesSet() {
        // 값의 관계: Pong이 끊긴 것을 알아채기 전에 세션이 먼저 끝나면 안 된다
        Duration detection = properties.pingInterval().multipliedBy(properties.maxMissedPongs());
        if (detection.compareTo(sessionTtl) >= 0) {
            throw new IllegalStateException("pw01.realtime.ping-interval × max-missed-pongs(" + detection
                    + ")는 pw01.auth.session.ttl(" + sessionTtl + ")보다 짧아야 합니다");
        }

        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("realtime-heartbeat-");
        scheduler.initialize();
        Duration interval = properties.pingInterval();
        scheduler.scheduleWithFixedDelay(this::sendPings, Instant.now().plus(interval), interval);
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /** 모든 연결에 Ping. 한 연결의 실패가 다른 연결에 영향을 주지 않게 연결마다 잡는다 */
    void sendPings() {
        for (RealtimeConnection connection : registry.all()) {
            try {
                if (!connection.isOpen()) {
                    continue;
                }
                if (connection.missedPongs() >= properties.maxMissedPongs()) {
                    log.info("[Realtime] heartbeat timeout {}", connection);
                    connection.close(RealtimeCloseStatus.HEARTBEAT_TIMEOUT);
                    continue;
                }
                connection.markPingSent();
                connection.send(new PingMessage());
            } catch (Exception e) {
                log.warn("[Realtime] ping failed {}", connection, e);
            }
        }
    }

    /**
     * Pong을 받았다: 세션을 다시 건다. 결과가 "다른 곳 로그인"이면 알리고 4001, 세션이 없으면 4002로 닫는다.
     * Redis에 닿지 못하면 연결은 그대로 둔다(세션 상태를 모르므로, 다음 Pong에서 다시 본다).
     */
    public void onPong(RealtimeConnection connection) {
        connection.markPongReceived();
        try {
            authService.extendSession(connection.token());
            // 게임(UE)이 Ping에 Pong으로 답하는지 확인할 때 켠다: LOGGING_LEVEL_COM_PW01_WEBSERVER_REALTIME=DEBUG
            log.debug("[Realtime] pong received, session extended {}", connection);
        } catch (UnauthorizedException e) {
            if (AuthService.isSessionReplaced(e)) {
                notifier.notifySessionReplaced(connection);
            } else {
                log.info("[Realtime] session ended code={} {}", e.getCode(), connection);
                connection.close(RealtimeCloseStatus.SESSION_ENDED);
            }
        } catch (DataAccessResourceFailureException e) {
            log.warn("[Realtime] session extend failed (store unavailable) {}", connection, e);
        }
    }

}
