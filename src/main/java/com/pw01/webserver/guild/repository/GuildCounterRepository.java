package com.pw01.webserver.guild.repository;

import com.pw01.webserver.guild.entity.GuildCounter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildCounterRepository extends JpaRepository<GuildCounter, Integer> {

    /** 길드 수 줄(하나뿐). V10이 만들므로 없으면 마이그레이션이 빠진 것이다 */
    default GuildCounter getSingle() {
        return findById(GuildCounter.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("guild_counter 줄이 없습니다(V10 마이그레이션 확인)"));
    }

}
