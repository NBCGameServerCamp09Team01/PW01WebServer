package com.pw01.webserver.presence.service;

import com.pw01.webserver.realtime.event.RealtimeAliveEvent;
import com.pw01.webserver.realtime.event.RealtimeConnectedEvent;
import com.pw01.webserver.realtime.event.RealtimeDisconnectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 실시간 연결 이벤트(realtime/event)를 받아 접속 상태를 고친다. realtime 안을 고치지 않고 연결 변화를 받는 자리다.
 * 연결·Pong 처리 스레드에서 바로 불린다. PresenceService가 Redis 오류를 삼키므로 연결 처리를 막지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnected(RealtimeConnectedEvent event) {
        presenceService.connected(event.accountId(), event.serverId());
    }

    @EventListener
    public void onAlive(RealtimeAliveEvent event) {
        presenceService.alive(event.accountId());
    }

    @EventListener
    public void onDisconnected(RealtimeDisconnectedEvent event) {
        presenceService.disconnected(event.accountId(), event.serverId());
    }

}
