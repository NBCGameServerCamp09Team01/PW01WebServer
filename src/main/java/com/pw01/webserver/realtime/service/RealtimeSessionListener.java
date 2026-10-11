package com.pw01.webserver.realtime.service;

import com.pw01.webserver.auth.event.SessionEndedEvent;
import com.pw01.webserver.auth.event.SessionIssuedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 로그인·로그아웃(auth)에서 생긴 세션 변화를 실시간 연결에 반영한다.
 * 이벤트는 로그인·로그아웃 요청과 같은 스레드에서 바로 불리므로, 여기서 생긴 예외가 로그인·로그아웃을 실패시키지 않게 모두 잡는다.
 */
@Component
public class RealtimeSessionListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionListener.class);

    private final RealtimeConnectionRegistry registry;
    private final RealtimeNotifier notifier;

    public RealtimeSessionListener(RealtimeConnectionRegistry registry, RealtimeNotifier notifier) {
        this.registry = registry;
        this.notifier = notifier;
    }

    /**
     * 새 세션이 생겼다. 이 순간 이 계정에 붙어 있는 연결은 이전 세션의 것이므로(새 세션은 아직 연결 전)
     * "다른 곳 로그인"을 알리고 4001로 닫는다(realtime-api.md "같은 계정의 연결이 겹칠 때").
     */
    @EventListener
    public void onSessionIssued(SessionIssuedEvent event) {
        try {
            registry.find(event.accountId()).ifPresent(notifier::notifySessionReplaced);
        } catch (RuntimeException e) {
            log.warn("[Realtime] could not notify session replaced. accountId={}", event.accountId(), e);
        }
    }

    /** 로그아웃: 그 세션의 연결만 1000으로 닫는다(그사이 다른 곳에서 로그인한 새 세션의 연결은 두기) */
    @EventListener
    public void onSessionEnded(SessionEndedEvent event) {
        try {
            registry.find(event.accountId())
                    .filter(connection -> connection.hasSameToken(event.token()))
                    .ifPresent(connection -> connection.close(RealtimeCloseStatus.LOGGED_OUT));
        } catch (RuntimeException e) {
            log.warn("[Realtime] could not close on logout. accountId={}", event.accountId(), e);
        }
    }

}
