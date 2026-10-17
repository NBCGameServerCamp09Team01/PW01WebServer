package com.pw01.webserver.guild.service;

import com.pw01.webserver.common.chat.GuildDirectory;
import com.pw01.webserver.presence.event.PresenceChangedEvent;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GuildNotifier는 보내기가 실패해도 예외를 밖으로 던지지 않는다(커밋 뒤라 데이터는 이미 저장됨, 응답은 성공이어야 함).
 * GuildPresenceNotifier(접속 표시, Lv2)도 같다: 연결 처리 스레드에서 불리므로 던지면 연결 처리가 막힌다.
 * 실패하면 GuildNotifier.onNotice·GuildPresenceNotifier.onPresenceChanged의 try·catch를 먼저 의심한다.
 */
class GuildNotifierUnitTest {

    // 확인: RealtimeNotifier가 예외를 던져도 onNotice는 조용히 끝난다(경고 로그만)
    @Test
    void 보내기_실패는_삼킴() {
        RealtimeNotifier realtime = mock(RealtimeNotifier.class);
        doThrow(new IllegalStateException("redis down")).when(realtime).sendToGuild(anyString(), any(), any());
        GuildNotifier notifier = new GuildNotifier(realtime);

        assertThatCode(() -> notifier.onNotice(new GuildRealtimeNotice("7", List.of(1L),
                RealtimeMessageType.GUILD_DISBANDED, Map.of("guildId", "7")))).doesNotThrowAnyException();
    }

    // 확인: 접속 알림도 보내기가 실패하면 조용히 끝난다(경고 로그만)
    @Test
    void 접속_알림_보내기_실패는_삼킴() {
        RealtimeNotifier realtime = mock(RealtimeNotifier.class);
        GuildDirectory directory = mock(GuildDirectory.class);
        when(directory.guildIdOf(1L)).thenReturn(Optional.of("7"));
        when(directory.memberIds("7")).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("redis down")).when(realtime).sendToGuild(anyString(), any(), any());
        GuildPresenceNotifier notifier = new GuildPresenceNotifier(directory, realtime);

        assertThatCode(() -> notifier.onPresenceChanged(new PresenceChangedEvent(1L, true))).doesNotThrowAnyException();
    }

}
