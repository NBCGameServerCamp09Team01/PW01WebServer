package com.pw01.webserver.guild.service;

import com.pw01.webserver.realtime.dto.RealtimeMessageType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 길드 모듈 안의 이벤트: "커밋되면 이 사람들에게 이 실시간 메시지를 보내라". GuildMembershipTx가 트랜잭션 안에서 내고,
 * GuildNotifier가 커밋 뒤(AFTER_COMMIT)에 받아 RealtimeNotifier.sendToGuild로 보낸다.
 * <p>
 * 받는 사람과 data는 트랜잭션 안에서 계산해 담는다(커밋된 상태와 같은 값). 커밋 뒤에 DB를 다시 읽지 않는다.
 * common/chat의 길드 생애 이벤트(채팅 모듈과의 계약)와 따로 둔다: 계약은 채팅이 필요한 것만 담고,
 * 실시간 알림에 필요한 신청자 목록·닉네임 등은 길드 내부 사정이라 계약을 넓히지 않는다.
 *
 * @param guildId    길드 채널 이름이 되는 길드 ID
 * @param recipients 받는 계정
 * @param type       메시지 종류(realtime-api.md "메시지 종류")
 * @param data       메시지 data(넣은 순서대로 JSON에 나감)
 */
public record GuildRealtimeNotice(String guildId, List<Long> recipients, RealtimeMessageType type,
                                  Map<String, Object> data) {

    public GuildRealtimeNotice {
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(type, "type");
        recipients = List.copyOf(recipients);
        data = new LinkedHashMap<>(data);
    }

}
