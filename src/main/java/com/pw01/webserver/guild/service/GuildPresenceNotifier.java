package com.pw01.webserver.guild.service;

import com.pw01.webserver.common.chat.GuildDirectory;
import com.pw01.webserver.presence.event.PresenceChangedEvent;
import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 접속 상태가 바뀌면 그 사람의 길드원에게 PRESENCE_CHANGED를 보낸다(realtime-api.md "메시지 종류").
 * 받는 사람은 본인을 뺀 길드원. 미소속이거나 혼자인 길드면 보내지 않는다.
 * 연결 처리 스레드에서 바로 불린다(DB 읽기 둘: 소속, 길드원 30명 이하). DB 트랜잭션 밖이라 AFTER_COMMIT이 아니라 바로 받는다.
 * 예외는 밖으로 던지지 않는다(접속 표시는 덤, 연결을 막으면 안 됨). 놓친 알림은 다음 G4에서 맞춰진다.
 */
@Component
@RequiredArgsConstructor
public class GuildPresenceNotifier {

    private static final Logger log = LoggerFactory.getLogger(GuildPresenceNotifier.class);

    private final GuildDirectory guildDirectory;
    private final RealtimeNotifier realtimeNotifier;

    @EventListener
    public void onPresenceChanged(PresenceChangedEvent event) {
        try {
            Optional<String> guildId = guildDirectory.guildIdOf(event.accountId());
            if (guildId.isEmpty()) {
                return;
            }
            List<Long> recipients = guildDirectory.memberIds(guildId.get()).stream()
                    .filter(memberId -> !memberId.equals(event.accountId()))
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }
            realtimeNotifier.sendToGuild(guildId.get(), recipients, RealtimeMessage.of(RealtimeMessageType.PRESENCE_CHANGED, Map.<String, Object>of(
                    "guildId", guildId.get(),
                    "accountId", event.accountId().toString(),
                    "online", event.online())));
        } catch (RuntimeException e) {
            log.warn("[Guild] presence notice failed accountId={} online={}", event.accountId(), event.online(), e);
        }
    }

}
