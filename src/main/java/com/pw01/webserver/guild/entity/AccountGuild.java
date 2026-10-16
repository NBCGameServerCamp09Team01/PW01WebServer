package com.pw01.webserver.guild.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 계정마다 길드 소속 한 줄(account_guild, "사람 줄"). 기본 키가 계정이라 한 사람은 길드 하나에만 든다.
 * 무소속이면 guildId·role·joinedAt이 모두 null, 소속이면 모두 값이다(DB CHECK ck_account_guild_membership).
 * version(@Version)이 같은 사람을 두 길드가 동시에 받거나, 같은 사람이 동시에 신청하는 것을 줄 세운다.
 * 해체는 이 엔티티 대신 저장소의 벌크 clearGuild로 소속을 한꺼번에 비운다.
 */
@Entity
@Table(name = "account_guild")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountGuild extends BaseEntity {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    private Long guildId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private GuildRole role;

    private Instant joinedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** 무소속 줄(가입 때 만든다) */
    public static AccountGuild unaffiliated(Long accountId) {
        Objects.requireNonNull(accountId, "accountId");
        AccountGuild person = new AccountGuild();
        person.accountId = accountId;
        return person;
    }

    public boolean isInGuild() {
        return guildId != null;
    }

    public boolean isLeader() {
        return role == GuildRole.LEADER;
    }

    public boolean belongsTo(Long guildId) {
        return this.guildId != null && this.guildId.equals(guildId);
    }

    /** 길드에 든다(만들기 = LEADER, 수락 = MEMBER). 이미 길드가 있으면 부르지 않는다(서비스가 먼저 판정 → GUILD_ALREADY_JOINED) */
    public void join(Long guildId, GuildRole role, Instant joinedAt) {
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
        if (isInGuild()) {
            throw new IllegalStateException("이미 길드가 있는 계정입니다: account " + accountId + ", guild " + this.guildId);
        }
        this.guildId = guildId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    /** 길드에서 나간다(탈퇴·내보내기). 무소속이면 부르지 않는다 */
    public void leave() {
        if (!isInGuild()) {
            throw new IllegalStateException("길드가 없는 계정입니다: account " + accountId);
        }
        this.guildId = null;
        this.role = null;
        this.joinedAt = null;
    }

}
