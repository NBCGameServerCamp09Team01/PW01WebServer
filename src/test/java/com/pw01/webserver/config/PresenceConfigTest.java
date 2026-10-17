package com.pw01.webserver.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 접속 상태 키 수명이 Ping 간격보다 길어야 서버가 뜨는지 본다(같거나 짧으면 Pong 사이에 키가 사라짐).
 * 실패하면 PresenceConfig.checkTtl의 비교(<=)를 먼저 의심한다.
 */
class PresenceConfigTest {

    private static final RealtimeProperties REALTIME = new RealtimeProperties(Duration.ofSeconds(60), 3);

    // 확인: 150초 > 60초는 됨, 60초(같음)·30초는 안 됨
    @Test
    void 수명은_Ping_간격보다_길어야() {
        assertThatCode(() -> PresenceConfig.checkTtl(new PresenceProperties(Duration.ofSeconds(150)), REALTIME))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PresenceConfig.checkTtl(new PresenceProperties(Duration.ofSeconds(60)), REALTIME))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PresenceConfig.checkTtl(new PresenceProperties(Duration.ofSeconds(30)), REALTIME))
                .isInstanceOf(IllegalStateException.class);
    }

    // 확인: 1초보다 짧은 값·빈 값은 설정 자체가 안 됨
    @Test
    void 수명은_1초_이상() {
        assertThatThrownBy(() -> new PresenceProperties(Duration.ofMillis(500))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PresenceProperties(null)).isInstanceOf(IllegalArgumentException.class);
    }

}
