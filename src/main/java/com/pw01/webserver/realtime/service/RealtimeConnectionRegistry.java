package com.pw01.webserver.realtime.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 계정 → 실시간 연결(계정당 하나, 새 것이 이김). 이 서버의 메모리에만 있다(루트 docs/contracts/realtime-api.md "서버 여러 대").
 * 여기서는 목록만 다루고, 밀려난 연결에 알리거나 닫는 일은 부른 쪽(RealtimeWebSocketHandler)이 한다.
 */
@Component
public class RealtimeConnectionRegistry {

    private final ConcurrentHashMap<Long, RealtimeConnection> connections = new ConcurrentHashMap<>();

    /**
     * 연결을 등록한다. 같은 계정의 연결이 이미 있으면 바꾸고 그 이전 연결을 돌려준다(부른 쪽이 닫는다).
     */
    public Optional<RealtimeConnection> register(RealtimeConnection connection) {
        RealtimeConnection previous = connections.put(connection.accountId(), connection);
        return Optional.ofNullable(previous).filter(p -> p != connection);
    }

    /** 이 연결이 아직 등록돼 있을 때만 뺀다. 밀려난 이전 연결이 닫히면서 새 연결을 지우지 않게 한다 */
    public void unregister(RealtimeConnection connection) {
        connections.remove(connection.accountId(), connection);
    }

    public Optional<RealtimeConnection> find(Long accountId) {
        return Optional.ofNullable(connections.get(accountId));
    }

    /** 지금 등록된 연결의 복사본(도는 동안 등록·해제가 일어나도 안전) */
    public Collection<RealtimeConnection> all() {
        return List.copyOf(connections.values());
    }

}
