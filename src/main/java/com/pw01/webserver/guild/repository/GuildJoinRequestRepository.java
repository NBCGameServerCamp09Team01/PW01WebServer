package com.pw01.webserver.guild.repository;

import com.pw01.webserver.guild.entity.GuildJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 대기 중 신청. 유효 기간은 표에 없어서(신청 시각 + 설정 값) 만료된 행도 읽히며, 거르는 일은 서비스가 한다.
 */
public interface GuildJoinRequestRepository extends JpaRepository<GuildJoinRequest, Long> {

    /** 내 신청(오래된 순) */
    List<GuildJoinRequest> findAllByAccountIdOrderByIdAsc(Long accountId);

    /** 길드가 받은 신청(오래된 순). 인덱스 idx_guild_join_request_guild */
    List<GuildJoinRequest> findAllByGuildIdOrderByIdAsc(Long guildId);

    Optional<GuildJoinRequest> findByAccountIdAndGuildId(Long accountId, Long guildId);

    /** 길드가 받은 유효한 신청 수(G4 pendingJoinRequestCount). validAfter = 지금 − 유효 기간(그보다 뒤에 들어온 신청만) */
    long countByGuildIdAndCreatedAtAfter(Long guildId, Instant validAfter);

    /** 수락·거절: 그 신청이 내 길드 것일 때만 */
    Optional<GuildJoinRequest> findByIdAndGuildId(Long id, Long guildId);

    /**
     * 해체 전용: 그 길드 신청자 계정 ID를 잠금 읽기(FOR UPDATE)로 읽는다(해체 알림 대상). 일반 SELECT는 스냅숏이라
     * 스냅숏 뒤에 들어온 신청자를 놓칠 수 있다. 곧 지울 행이라 잠가도 잠금 순서(guild → guild_join_request)를 어기지 않는다.
     */
    @Query(value = "SELECT account_id FROM guild_join_request WHERE guild_id = :guildId ORDER BY account_id FOR UPDATE",
            nativeQuery = true)
    List<Long> lockAccountIdsByGuildId(@Param("guildId") Long guildId);

    /** 해체 알림 대상(신청자) */
    @Query("SELECT r.accountId FROM GuildJoinRequest r WHERE r.guildId = :guildId ORDER BY r.accountId")
    List<Long> findAccountIdsByGuildId(@Param("guildId") Long guildId);

    /**
     * 한 사람의 신청을 모두 지운다(수락되거나 길드를 만들면). 트랜잭션의 마지막 쓰기로 부른다.
     * 부른 쪽이 읽어 둔 엔티티(길드·account_guild 행)를 놓지 않도록 영속성 컨텍스트를 비우지 않는다. 지운 신청 엔티티는 다시 쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GuildJoinRequest r WHERE r.accountId = :accountId")
    int deleteAllOfAccount(@Param("accountId") Long accountId);

    /** 신청 취소(G6): 내 신청일 때만 지운다. 없거나 남의 신청이면 0행(호출한 쪽은 그래도 204) */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GuildJoinRequest r WHERE r.id = :id AND r.accountId = :accountId")
    int deleteOwn(@Param("id") Long id, @Param("accountId") Long accountId);

    /** 해체 전용: 그 길드가 받은 신청을 모두 지운다. AccountGuildRepository.clearGuild와 같이 전후로 flush·clear */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GuildJoinRequest r WHERE r.guildId = :guildId")
    int deleteAllOfGuild(@Param("guildId") Long guildId);

}
