package com.pw01.webserver.common.chat;

import java.util.Objects;

/**
 * 길드 생애 이벤트: 누가 길드에서 나갔다(탈퇴·내보내기). 해체로 모두 나간 것은 GuildDisbandedEvent 하나로 낸다.
 * 내는 법·받는 법은 GuildMemberJoinedEvent와 같다(트랜잭션 안에서 냄, 받는 쪽은 AFTER_COMMIT).
 *
 * @param guildId   나간 길드 ID
 * @param accountId 나간 계정
 * @param reason    LEFT(스스로 탈퇴) · KICKED(길드장이 내보냄 — 내보내기 기능이 생기면 냄)
 */
public record GuildMemberLeftEvent(String guildId, Long accountId, Reason reason) {

    public GuildMemberLeftEvent {
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(reason, "reason");
    }

    /** 나간 까닭. 실시간 GUILD_MEMBER_LEFT의 reason과 같은 이름이다 */
    public enum Reason {
        LEFT,
        KICKED
    }

}
