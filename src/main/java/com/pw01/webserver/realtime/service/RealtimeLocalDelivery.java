package com.pw01.webserver.realtime.service;

import com.pw01.webserver.realtime.dto.RealtimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

/**
 * 이 서버에 붙은 연결에만 보낸다. 다른 서버로 넘기는 일은 하지 않는다(그건 RealtimeNotifier가 RealtimeRelayPublisher로).
 * 보내는 쪽(RealtimeNotifier)과 다른 서버에서 받은 쪽(RealtimeRelayListener)이 같이 쓴다.
 * 놓칠 수 있다: 연결이 없거나 보내기에 실패하면 건너뛰고, 상태는 게임이 HTTP로 다시 받는다(realtime-api.md "원칙").
 */
@Service
public class RealtimeLocalDelivery {

    private static final Logger log = LoggerFactory.getLogger(RealtimeLocalDelivery.class);

    private final RealtimeConnectionRegistry registry;
    private final JsonMapper jsonMapper;

    public RealtimeLocalDelivery(RealtimeConnectionRegistry registry, JsonMapper jsonMapper) {
        this.registry = registry;
        this.jsonMapper = jsonMapper;
    }

    /** 이 서버에 붙어 있으면 보낸다. 보냈으면 true */
    public boolean deliverToAccount(Long accountId, RealtimeMessage message) {
        Optional<RealtimeConnection> connection = registry.find(accountId);
        return connection.isPresent() && sendTo(connection.get(), toText(message), message);
    }

    /** 목록 가운데 이 서버에 붙은 사람에게만 보낸다. 보낸 수 */
    public int deliverToAccounts(Collection<Long> accountIds, RealtimeMessage message) {
        TextMessage text = toText(message);
        int sent = 0;
        for (Long accountId : accountIds) {
            Optional<RealtimeConnection> connection = registry.find(accountId);
            if (connection.isPresent() && sendTo(connection.get(), text, message)) {
                sent++;
            }
        }
        return sent;
    }

    /** 이 서버의 모든 연결에 보낸다. 보낸 수 */
    public int deliverToAll(RealtimeMessage message) {
        TextMessage text = toText(message);
        int sent = 0;
        for (RealtimeConnection connection : registry.all()) {
            if (sendTo(connection, text, message)) {
                sent++;
            }
        }
        return sent;
    }

    /** 보내고 닫는다. 보내기에 실패해도 닫는다(게임은 종료 코드만으로도 같은 처리를 한다) */
    void sendAndClose(RealtimeConnection connection, RealtimeMessage message, CloseStatus status) {
        try {
            sendTo(connection, toText(message), message);
        } finally {
            connection.close(status);
        }
    }

    private TextMessage toText(RealtimeMessage message) {
        return new TextMessage(jsonMapper.writeValueAsString(message));
    }

    private boolean sendTo(RealtimeConnection connection, TextMessage text, RealtimeMessage message) {
        if (!connection.isOpen()) {
            return false;
        }
        try {
            connection.send(text);
            log.info("[Realtime] sent type={} id={} {}", message.type(), message.id(), connection);
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("[Realtime] send failed type={} id={} {}", message.type(), message.id(), connection, e);
            return false;
        }
    }

}
