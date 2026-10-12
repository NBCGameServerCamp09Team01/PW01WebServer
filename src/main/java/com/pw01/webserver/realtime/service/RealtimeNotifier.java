package com.pw01.webserver.realtime.service;

import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 게임에 알림을 보내는 입구. 다른 기능(우편함·공지·이용 제한)은 이 클래스의 send만 부른다(뼈대).
 * 알림은 놓칠 수 있다: 연결이 없거나 보내기에 실패하면 false이고, 상태는 게임이 HTTP로 다시 받는다(realtime-api.md "원칙").
 */
@Service
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final RealtimeConnectionRegistry registry;
    private final JsonMapper jsonMapper;

    public RealtimeNotifier(RealtimeConnectionRegistry registry, JsonMapper jsonMapper) {
        this.registry = registry;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 이 계정의 연결에 메시지를 보낸다.
     *
     * @return 보냈으면 true. 연결이 없거나 보내지 못했으면 false
     */
    public boolean send(Long accountId, RealtimeMessage message) {
        return registry.find(accountId)
                .map(connection -> sendTo(connection, message))
                .orElse(false);
    }

    /** "다른 곳 로그인"을 알리고 4001로 닫는다. 보내기에 실패해도 닫는다(게임은 종료 코드만으로도 같은 처리를 한다) */
    public void notifySessionReplaced(RealtimeConnection connection) {
        sendAndClose(connection, RealtimeMessage.of(RealtimeMessageType.SESSION_REPLACED), RealtimeCloseStatus.SESSION_REPLACED);
    }

    void sendAndClose(RealtimeConnection connection, RealtimeMessage message, CloseStatus status) {
        try {
            sendTo(connection, message);
        } finally {
            connection.close(status);
        }
    }

    private boolean sendTo(RealtimeConnection connection, RealtimeMessage message) {
        if (!connection.isOpen()) {
            return false;
        }
        try {
            connection.send(new TextMessage(jsonMapper.writeValueAsString(message)));
            log.info("[Realtime] sent type={} id={} {}", message.type(), message.id(), connection);
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("[Realtime] send failed type={} id={} {}", message.type(), message.id(), connection, e);
            return false;
        }
    }

}
