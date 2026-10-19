package com.pw01.webserver.presence.service;

import com.pw01.webserver.presence.event.PresenceChangedEvent;
import com.pw01.webserver.presence.repository.RedisPresenceStore;
import com.pw01.webserver.common.server.ServerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

/**
 * 누가 지금 실시간 연결을 갖고 있나(서버 여러 대 공통). 길드원 목록의 접속 표시에 쓰고, 친구 기능도 그대로 쓴다.
 * 쓰기는 연결 이벤트(PresenceEventListener)가 부르고, 읽기는 길드 조회가 부른다.
 * 상태가 실제로 바뀌면(키가 새로 생김 / 이 서버가 키를 지움) PresenceChangedEvent를 낸다. 누구에게 알릴지는 받는 쪽(길드)이 정한다.
 * Redis 장애는 밖으로 던지지 않는다: 쓰기·지우기 실패는 경고 로그(키 수명이 정리, 이벤트도 내지 않음), 읽기 실패는 "모두 접속 안 함".
 * 접속 표시는 덤이라, 이것 때문에 연결이나 길드 조회가 실패하면 안 된다.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final RedisPresenceStore store;
    private final ServerId serverId;
    private final ApplicationEventPublisher events;

    public PresenceService(RedisPresenceStore store, ServerId serverId, ApplicationEventPublisher events) {
        this.store = store;
        this.serverId = serverId;
        this.events = events;
    }

    /** 연결됨: 연결이 붙은 서버 번호로. 전에 키가 없었으면 접속함을 알린다 */
    public void connected(Long accountId, String connectedServerId) {
        boolean becameOnline;
        try {
            becameOnline = store.markOnline(accountId, connectedServerId);
        } catch (RuntimeException e) {
            log.warn("[Presence] mark online failed accountId={} cause={}", accountId, e.getClass().getSimpleName(), e);
            return;
        }
        if (becameOnline) {
            publish(accountId, true);
        }
    }

    /**
     * 살아 있음(Pong): 이벤트에 서버 번호가 없으므로 이 서버 번호로(Pong은 연결이 붙은 서버가 받음).
     * 보통은 키가 있어 알리지 않는다. 키가 수명이 다해 사라졌다가(Redis 재시작 등) 다시 생긴 경우만 접속함을 알린다.
     */
    public void alive(Long accountId) {
        connected(accountId, serverId.value());
    }

    /**
     * 끊김: 그 서버 번호일 때만 지운다. 같은 서버에서 새 연결로 바뀐 이전 연결은 realtime이 끊김을 내지 않으므로 여기 오지 않는다.
     * 실제로 지웠을 때만 끊김을 알린다(다른 서버로 이미 다시 붙었으면 지우지 않고 알리지도 않음).
     * 같은 서버에서 끊긴 직후 새로 붙은 경우 드물게 새 값까지 지울 수 있다 → 다음 Pong(최대 Ping 간격)에서 다시 채워지고 접속함도 다시 알린다.
     */
    public void disconnected(Long accountId, String connectedServerId) {
        boolean released;
        try {
            released = store.release(accountId, connectedServerId);
        } catch (RuntimeException e) {
            log.warn("[Presence] release failed accountId={} cause={}", accountId, e.getClass().getSimpleName(), e);
            return;
        }
        if (released) {
            publish(accountId, false);
        }
    }

    /** 이 가운데 접속한 계정. Redis에 닿지 못하면 빈 집합(모두 접속 안 함으로 보임) */
    public Set<Long> onlineOf(Collection<Long> accountIds) {
        try {
            return store.onlineOf(accountIds);
        } catch (RuntimeException e) {
            log.warn("[Presence] read failed size={} cause={}", accountIds.size(), e.getClass().getSimpleName(), e);
            return Set.of();
        }
    }

    /** 받는 쪽이 실패해도 연결 처리를 막지 않는다 */
    private void publish(Long accountId, boolean online) {
        try {
            events.publishEvent(new PresenceChangedEvent(accountId, online));
        } catch (RuntimeException e) {
            log.warn("[Presence] changed listener failed accountId={} online={}", accountId, online, e);
        }
    }

}
