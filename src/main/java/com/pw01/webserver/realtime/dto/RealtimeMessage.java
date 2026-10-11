package com.pw01.webserver.realtime.dto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * 서버 → 게임 메시지 봉투. 모양은 루트 docs/contracts/realtime-api.md "봉투"가 기준이다.
 * 알림 줄의 다음 기능(우편함·공지·이용 제한)도 이 봉투를 그대로 쓰고 type과 data만 다르게 채운다.
 *
 * @param type   메시지 종류(RealtimeMessageType 이름)
 * @param id     메시지 번호(UUID). 게임은 같은 번호를 한 번만 처리한다
 * @param sentAt 보낸 시각(UTC, 밀리초까지)
 * @param data   종류별 내용. 내용이 없으면 빈 객체(null이 아님)
 */
public record RealtimeMessage(String type, String id, Instant sentAt, Map<String, Object> data) {

    public static RealtimeMessage of(RealtimeMessageType type) {
        return of(type, Map.of());
    }

    public static RealtimeMessage of(RealtimeMessageType type, Map<String, Object> data) {
        return new RealtimeMessage(type.name(), UUID.randomUUID().toString(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS), Map.copyOf(data));
    }

}
