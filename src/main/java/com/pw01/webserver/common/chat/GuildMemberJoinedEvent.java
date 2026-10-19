package com.pw01.webserver.common.chat;

import java.util.Objects;

/**
 * 길드 생애 이벤트: 누가 길드원이 되었다(가입 신청 수락, 길드를 만든 사람 포함). 소셜(길드) 모듈이 낸다(M23 두 모듈 약속).
 * 소셜은 길드원 줄을 바꾸는 트랜잭션 안에서 Spring 이벤트로 내고, 받는 쪽은 @TransactionalEventListener(AFTER_COMMIT)로 받는다
 * (되돌려진 트랜잭션의 이벤트는 받지 않는다). 받는 쪽이 DB에 쓰려면 REQUIRES_NEW 트랜잭션을 연다.
 * 게임에 가는 알림(GUILD_MEMBER_JOINED)과는 따로다 — 이것은 서버 안에서 다른 모듈(채팅)이 길드 안을 몰라도 붙는 자리다.
 *
 * @param guildId   길드 ID(GuildDirectory와 같은 문자열)
 * @param accountId 길드원이 된 계정
 */
public record GuildMemberJoinedEvent(String guildId, Long accountId) {

    public GuildMemberJoinedEvent {
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(accountId, "accountId");
    }

}
