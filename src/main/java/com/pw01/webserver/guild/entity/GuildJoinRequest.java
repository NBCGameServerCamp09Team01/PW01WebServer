package com.pw01.webserver.guild.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 대기 중 가입 신청 한 행(guild_join_request). 수락·거절·취소·해체되면 지운다.
 * 유효 기간은 칼럼으로 두지 않고 신청 시각(createdAt) + 설정 값으로 계산한다(설정을 바꾸면 기존 신청에도 바로 적용).
 * 같은 사람·같은 길드는 한 행(유일 uk_guild_join_request_account_guild).
 */
@Entity
@Table(name = "guild_join_request", uniqueConstraints = @UniqueConstraint(
        name = "uk_guild_join_request_account_guild", columnNames = {"account_id", "guild_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuildJoinRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guild_join_request_id")
    private Long id;

    @Column(nullable = false)
    private Long guildId;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuildJoinRequestKind kind;

    /** 사람이 길드에 보내는 신청 */
    public static GuildJoinRequest application(Long guildId, Long accountId) {
        GuildJoinRequest request = new GuildJoinRequest();
        request.guildId = Objects.requireNonNull(guildId, "guildId");
        request.accountId = Objects.requireNonNull(accountId, "accountId");
        request.kind = GuildJoinRequestKind.APPLICATION;
        return request;
    }

    /** 신청 시각. 저장(persist) 때 채워진다 */
    public Instant requestedAt() {
        return getCreatedAt();
    }

    public Instant expiresAt(Duration ttl) {
        return requestedAt().plus(ttl);
    }

    /** 유효 기간이 지났나. 끝 시각과 같으면 지난 것으로 본다 */
    public boolean isExpired(Instant now, Duration ttl) {
        return !now.isBefore(expiresAt(ttl));
    }

}
