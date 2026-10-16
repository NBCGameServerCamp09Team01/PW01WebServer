package com.pw01.webserver.guild.repository;

import com.pw01.webserver.guild.entity.Guild;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuildRepository extends JpaRepository<Guild, Long> {

    /**
     * 해체 전용: guild 행을 SELECT … FOR UPDATE로 맨 먼저 잡는다(비관적 락, 잠금 순서 "guild 행 → account_guild 행 → guild_join_request 행").
     * 잡는 동안 같은 길드의 수락·탈퇴는 guild 행 version UPDATE에서 기다렸다가, 해체가 커밋되면 0행으로 실패해 다시 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Guild g WHERE g.id = :guildId")
    Optional<Guild> findByIdForUpdate(@Param("guildId") Long guildId);

    /**
     * 해체 전용 벌크 DELETE. 해체는 앞선 벌크 UPDATE(clearAutomatically)로 영속성 컨텍스트가 비워져 guild 엔티티가 관리 밖이므로
     * 엔티티 삭제(delete) 대신 쿼리로 지운다. 소속(account_guild)·신청(guild_join_request)을 먼저 비워야 외래 키에 걸리지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Guild g WHERE g.id = :guildId")
    int deleteGuild(@Param("guildId") Long guildId);

    /** 이름이 이미 있나. name 칼럼이 utf8mb4_0900_bin이라 대소문자까지 같아야 같은 이름이다 */
    boolean existsByName(String name);

    /**
     * G1 목록 한 묶음: 최근 생성 순(guild_id 내림차순), 커서(앞 묶음 마지막 guild_id) 다음부터, 이름 앞부분 일치.
     * 값 커서라 묶음 사이에 길드가 생기거나 없어져도 겹치거나 빠지지 않는다(OFFSET과 다름).
     * LIKE는 name 칼럼 정렬 규칙(utf8mb4_0900_bin)을 따라 대소문자를 가린다. prefix에 %·_가 없다는 것은 서비스가 검사한다.
     *
     * @param cursor 이 ID보다 작은 길드부터. null이면 처음부터
     * @param prefix 이름 앞부분. null이면 전부
     * @param limit  가져올 최대 행 수(다음 묶음이 있는지 보려고 부르는 쪽이 size + 1을 넘김)
     */
    @Query("""
            SELECT g FROM Guild g
             WHERE (:cursor IS NULL OR g.id < :cursor)
               AND (:prefix IS NULL OR g.name LIKE CONCAT(:prefix, '%'))
             ORDER BY g.id DESC""")
    List<Guild> findPage(@Param("cursor") Long cursor, @Param("prefix") String prefix, Limit limit);

}
