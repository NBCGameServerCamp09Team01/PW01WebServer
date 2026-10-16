package com.pw01.webserver.guild.service;

import com.pw01.webserver.realtime.dto.RealtimeMessage;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 길드 실시간 알림(realtime-api.md v1.4 GUILD_*)을 **커밋 뒤에** 보낸다.
 * <ul>
 *   <li>AFTER_COMMIT이라 롤백된 시도(ProgressWriter 재시도 중 실패한 트랜잭션)의 알림은 나가지 않는다(P-5).</li>
 *   <li>다른 서버에 붙은 사람에게도 길드 채널(Redis Pub/Sub)로 넘어간다(RealtimeNotifier.sendToGuild).</li>
 *   <li>예외를 밖으로 던지지 않는다. 커밋 뒤 콜백의 예외는 요청 응답까지 올라가는데, 데이터는 이미 저장되어 성공 응답이어야 한다.
 *       알림은 놓쳐도 되는 신호다(게임은 길드 화면을 열 때·다른 알림 때 G4로 다시 받는다).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GuildNotifier {

    private static final Logger log = LoggerFactory.getLogger(GuildNotifier.class);

    private final RealtimeNotifier realtimeNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotice(GuildRealtimeNotice notice) {
        try {
            realtimeNotifier.sendToGuild(notice.guildId(), notice.recipients(),
                    RealtimeMessage.of(notice.type(), notice.data()));
        } catch (RuntimeException e) {
            log.warn("[Guild] realtime notice failed type={} guildId={} recipients={}",
                    notice.type(), notice.guildId(), notice.recipients().size(), e);
        }
    }

}
