package com.pw01.webserver.realtime.service;

import com.pw01.webserver.realtime.event.RealtimeAliveEvent;
import com.pw01.webserver.realtime.event.RealtimeConnectedEvent;
import com.pw01.webserver.realtime.event.RealtimeDisconnectedEvent;
import com.pw01.webserver.realtime.event.ServerContentChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 연결 이벤트(연결됨·끊김·살아 있음)와 서버 사이 알림 이벤트(정의 바뀜)를 낸다. 다른 기능(접속 상태)이 realtime 안을 고치지 않고 연결 변화를 알게 하는 자리다.
 * Spring 이벤트는 받는 쪽 예외가 내는 쪽으로 올라오므로 여기서 모두 잡는다(받는 쪽 실수로 연결·Pong 처리가 깨지지 않게).
 */
@Component
public class RealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisher.class);

    private final ApplicationEventPublisher publisher;
    private final RealtimeServerId serverId;

    public RealtimeEventPublisher(ApplicationEventPublisher publisher, RealtimeServerId serverId) {
        this.publisher = publisher;
        this.serverId = serverId;
    }

    public void connected(Long accountId) {
        publish(new RealtimeConnectedEvent(accountId, serverId.value()));
    }

    public void disconnected(Long accountId, int closeCode) {
        publish(new RealtimeDisconnectedEvent(accountId, serverId.value(), closeCode));
    }

    public void alive(Long accountId) {
        publish(new RealtimeAliveEvent(accountId));
    }

    /** 다른 서버가 웨이브 정의를 바꿨다(CONTENT_CHANGED를 받음). 받는 쪽은 오래 걸리는 일을 이 스레드에서 하지 않는다 */
    public void contentChanged(String origin) {
        publish(new ServerContentChangedEvent(origin));
    }

    private void publish(Object event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("[Realtime] event listener failed event={}", event, e);
        }
    }

}
