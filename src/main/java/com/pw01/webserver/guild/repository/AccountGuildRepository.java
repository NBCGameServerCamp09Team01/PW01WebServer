package com.pw01.webserver.guild.repository;

import com.pw01.webserver.guild.entity.AccountGuild;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountGuildRepository extends JpaRepository<AccountGuild, Long> {

    /**
     * 신청 전용: 내 account_guild 행을 잡으면서 version을 곧바로 올린다(PESSIMISTIC_FORCE_INCREMENT = FOR UPDATE + version UPDATE).
     * 같은 사람의 동시 신청이 여기서 줄 서므로 "대기 중 3곳"을 센 값이 믿을 만하고, 잠금 순서 "account_guild 행 → guild_join_request 행"이 지켜진다.
     */
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("SELECT a FROM AccountGuild a WHERE a.accountId = :accountId")
    Optional<AccountGuild> findForApplyById(@Param("accountId") Long accountId);

    /**
     * 잠그며 읽기(FOR UPDATE). 잠금 읽기는 트랜잭션 스냅숏이 아니라 가장 최근에 커밋된 행을 본다.
     * insertIfAbsent 바로 뒤에 쓴다: 다른 트랜잭션이 먼저 넣은 행은 보통 읽기(스냅숏)로는 안 보인다(REPEATABLE READ).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountGuild a WHERE a.accountId = :accountId")
    Optional<AccountGuild> findLockedById(@Param("accountId") Long accountId);

    /** 길드원 account_guild 행(길드장 먼저 세우기는 서비스가 한다). 인덱스 idx_account_guild_guild_joined 범위 읽기 */
    List<AccountGuild> findAllByGuildIdOrderByJoinedAtAsc(Long guildId);

    /** 길드원 계정 ID만(GuildDirectory.memberIds, 알림 받는 사람, 해체 직전 목록). 캐시 없이 MySQL에서 바로 읽는다 */
    @Query("SELECT a.accountId FROM AccountGuild a WHERE a.guildId = :guildId ORDER BY a.accountId")
    List<Long> findAccountIdsByGuildId(@Param("guildId") Long guildId);

    /**
     * 무소속 account_guild 행을 넣는다. 이미 있으면 아무것도 바꾸지 않는다(예외 없음). 가입 이벤트 뒤에도 행이 없을 때(섞여 도는 배포,
     * 동시에 두 번) 길드 기능이 그 자리에서 부른다. AccountStageProgressRepository.insertIfAbsent와 같은 방식
     * (INSERT IGNORE는 외래 키·CHECK 위반까지 넘기므로 쓰지 않음, 반환 행 수로 "새로 넣음"을 판단하지 않음).
     * 부른 쪽 트랜잭션의 엔티티를 놓지 않도록 영속성 컨텍스트를 비우지 않고, 쿼리 전에 미반영 변경만 내보낸다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
            VALUES (:accountId, NULL, NULL, NULL, 0, :now, :now)
            ON DUPLICATE KEY UPDATE account_id = account_id""", nativeQuery = true)
    int insertIfAbsent(@Param("accountId") Long accountId, @Param("now") Instant now);

    /**
     * 해체 전용 벌크: 그 길드원 모두를 무소속으로 하고 version을 올린다(그사이 그 account_guild 행을 고치던 트랜잭션은 낙관적 락으로 실패).
     * 벌크 JPQL은 영속성 컨텍스트를 건너뛰므로 전에 내보내고(flush) 뒤에 비운다(clear). 비운 뒤에는 앞서 읽은 엔티티를
     * 다시 고치지 않는다(관리 밖이라 저장되지 않음). 감사 칼럼(updatedAt)도 직접 넣는다.
     *
     * @return 무소속이 된 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE AccountGuild a
               SET a.guildId = NULL, a.role = NULL, a.joinedAt = NULL, a.version = a.version + 1, a.updatedAt = :now
             WHERE a.guildId = :guildId""")
    int clearGuild(@Param("guildId") Long guildId, @Param("now") Instant now);

}
