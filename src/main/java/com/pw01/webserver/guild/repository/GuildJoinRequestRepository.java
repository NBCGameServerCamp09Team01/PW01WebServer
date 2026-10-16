package com.pw01.webserver.guild.repository;

import com.pw01.webserver.guild.entity.GuildJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 대기 중 신청. 유효 기간은 표에 없어서(신청 시각 + 설정 값) 만료된 줄도 읽히며, 거르는 일은 서비스가 한다.
 */
public interface GuildJoinRequestRepository extends JpaRepository<GuildJoinRequest, Long> {

    /** 내 신청(오래된 순) */
    List<GuildJoinRequest> findAllByAccountIdOrderByIdAsc(Long accountId);

    /** 길드가 받은 신청(오래된 순). 인덱스 idx_guild_join_request_guild */
    List<GuildJoinRequest> findAllByGuildIdOrderByIdAsc(Long guildId);

    Optional<GuildJoinRequest> findByAccountIdAndGuildId(Long accountId, Long guildId);

    /** 수락·거절: 그 신청이 내 길드 것일 때만 */
    Optional<GuildJoinRequest> findByIdAndGuildId(Long id, Long guildId);

    /** 해체 알림 대상(신청자) */
    @Query("SELECT r.accountId FROM GuildJoinRequest r WHERE r.guildId = :guildId ORDER BY r.accountId")
    List<Long> findAccountIdsByGuildId(@Param("guildId") Long guildId);

    /**
     * 한 사람의 신청을 모두 지운다(수락되거나 길드를 만들면). 트랜잭션의 마지막 쓰기로 부른다.
     * 부른 쪽이 읽어 둔 엔티티(길드·사람 줄)를 놓지 않도록 영속성 컨텍스트를 비우지 않는다. 지운 신청 엔티티는 다시 쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GuildJoinRequest r WHERE r.accountId = :accountId")
    int deleteAllOfAccount(@Param("accountId") Long accountId);

    /** 해체 전용: 그 길드가 받은 신청을 모두 지운다. AccountGuildRepository.clearGuild와 같이 전후로 flush·clear */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GuildJoinRequest r WHERE r.guildId = :guildId")
    int deleteAllOfGuild(@Param("guildId") Long guildId);

}
