package com.pw01.webserver.common.chat;

import java.util.List;
import java.util.Objects;

/**
 * 길드 생애 이벤트: 길드가 해체되었다. 소셜은 길드·길드원 소속·가입 신청을 지운 트랜잭션 안에서 내고,
 * 채팅 모듈은 AFTER_COMMIT으로 받아 그 길드의 채팅 줄·캐시를 지운다(소셜은 채팅 표를 모른다 — 채팅 표는 길드 표에 외래 키를 걸지 않는다).
 * 받는 쪽이 DB에 쓰려면 REQUIRES_NEW 트랜잭션을 연다(AFTER_COMMIT 때 원래 트랜잭션은 이미 커밋되어 그 안의 쓰기는 반영되지 않는다).
 *
 * @param guildId         해체된 길드 ID
 * @param formerMemberIds 해체 직전의 길드원 전부(길드장 포함)
 */
public record GuildDisbandedEvent(String guildId, List<Long> formerMemberIds) {

    public GuildDisbandedEvent {
        Objects.requireNonNull(guildId, "guildId");
        formerMemberIds = List.copyOf(formerMemberIds);
    }

}
