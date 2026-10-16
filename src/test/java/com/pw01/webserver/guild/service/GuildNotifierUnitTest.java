package com.pw01.webserver.guild.service;

import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import com.pw01.webserver.realtime.service.RealtimeNotifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * GuildNotifier는 보내기가 실패해도 예외를 밖으로 던지지 않는다(커밋 뒤라 데이터는 이미 저장됨, 응답은 성공이어야 함).
 * 실패하면 GuildNotifier.onNotice의 try·catch를 먼저 의심한다.
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

}
