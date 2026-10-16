package com.pw01.webserver.guild.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서버 길드 수(guild_counter). 행은 하나(id = 1, V10이 만듦).
 * 만들기가 이 줄의 version을 올려 동시 만들기를 줄 세운다(잠금 순서의 맨 앞). 상한은 설정 값이라 서비스가 canCreate로 판정한다.
 */
@Entity
@Table(name = "guild_counter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuildCounter extends BaseEntity {

    /** 행 하나뿐인 guild_counter 행의 ID */
    public static final int SINGLE_ID = 1;

    @Id
    @Column(name = "counter_id")
    private Integer id;

    @Column(nullable = false)
    private int guildCount;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean canCreate(int maxGuilds) {
        return guildCount < maxGuilds;
    }

    /** 길드 하나를 만듦. 상한은 서비스가 canCreate로 먼저 판정한다(GUILD_LIMIT_REACHED) */
    public void increase() {
        guildCount++;
    }

    /** 길드 하나를 해체함 */
    public void decrease() {
        if (guildCount <= 0) {
            throw new IllegalStateException("길드 수가 0인데 해체를 셀 수 없습니다");
        }
        guildCount--;
    }

}
