package com.pw01.webserver.guild.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 길드 한 행(guild). 인원 칼럼(memberCount)은 account_guild 행(account_guild)의 실제 수와 같게, 같은 트랜잭션에서 고친다.
 * version(@Version)이 같은 길드를 동시에 고치는 수락·탈퇴를 줄 세운다(낙관적 락, 부딪히면 ProgressWriter가 다시 함).
 * 정원 판정은 서비스가 isFull()로 먼저 하고, 여기 메서드는 규칙을 어기면 IllegalStateException(서비스 버그)으로 막는다.
 * DB CHECK(ck_guild_member_count: 1 ≤ 인원 ≤ 정원)가 마지막 방어다.
 */
@Entity
@Table(name = "guild", uniqueConstraints = @UniqueConstraint(name = "uk_guild_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guild extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guild_id")
    private Long id;

    @Column(nullable = false, length = 12)
    private String name;

    @Column(nullable = false)
    private Long leaderAccountId;

    @Column(nullable = false)
    private int memberCount;

    @Column(nullable = false)
    private int maxMembers;

    @Version
    @Column(nullable = false)
    private Long version;

    /** 새 길드. 만든 사람이 길드장이고 첫 길드원이다(인원 1). 이름 규칙·겹침은 서비스와 DB가 판정한다 */
    public static Guild create(String name, Long leaderAccountId, int maxMembers) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(leaderAccountId, "leaderAccountId");
        if (maxMembers < 1) {
            throw new IllegalArgumentException("정원은 1 이상이어야 합니다: " + maxMembers);
        }
        Guild guild = new Guild();
        guild.name = name;
        guild.leaderAccountId = leaderAccountId;
        guild.memberCount = 1;
        guild.maxMembers = maxMembers;
        return guild;
    }

    public boolean isFull() {
        return memberCount >= maxMembers;
    }

    public boolean isLeader(Long accountId) {
        return leaderAccountId.equals(accountId);
    }

    /** 길드원 한 명이 들어옴. 정원이 찼으면 부르지 않는다(서비스가 isFull로 먼저 판정 → GUILD_FULL) */
    public void increaseMemberCount() {
        if (isFull()) {
            throw new IllegalStateException("정원이 찬 길드에 길드원을 더할 수 없습니다: guild " + id);
        }
        memberCount++;
    }

    /** 길드원 한 명이 나감(탈퇴·내보내기). 길드장 혼자 남은 길드는 나가는 대신 해체한다 */
    public void decreaseMemberCount() {
        if (memberCount <= 1) {
            throw new IllegalStateException("길드장만 남은 길드는 인원을 줄이지 않고 해체합니다: guild " + id);
        }
        memberCount--;
    }

}
