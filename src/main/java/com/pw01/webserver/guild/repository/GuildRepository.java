package com.pw01.webserver.guild.repository;

import com.pw01.webserver.guild.entity.Guild;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GuildRepository extends JpaRepository<Guild, Long> {

    /**
     * 해체 전용: 길드 줄을 SELECT … FOR UPDATE로 맨 먼저 잡는다(비관적 락, 잠금 순서 "길드 줄 → 사람 줄 → 신청 줄").
     * 잡는 동안 같은 길드의 수락·탈퇴는 길드 줄 version UPDATE에서 기다렸다가, 해체가 커밋되면 0줄로 실패해 다시 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Guild g WHERE g.id = :guildId")
    Optional<Guild> findByIdForUpdate(@Param("guildId") Long guildId);

    /** 이름이 이미 있나. name 칼럼이 utf8mb4_0900_bin이라 대소문자까지 같아야 같은 이름이다 */
    boolean existsByName(String name);

}
