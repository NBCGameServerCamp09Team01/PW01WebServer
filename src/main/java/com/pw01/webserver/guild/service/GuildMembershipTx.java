package com.pw01.webserver.guild.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.config.GuildProperties;
import com.pw01.webserver.guild.dto.GuildJoinRequestResponse;
import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.entity.Guild;
import com.pw01.webserver.guild.entity.GuildCounter;
import com.pw01.webserver.guild.entity.GuildJoinRequest;
import com.pw01.webserver.guild.entity.GuildRole;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import com.pw01.webserver.guild.repository.GuildCounterRepository;
import com.pw01.webserver.guild.repository.GuildJoinRequestRepository;
import com.pw01.webserver.guild.repository.GuildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 길드 쓰기의 트랜잭션 본문 모음. 잠금 순서(guild_counter → guild → account_guild → guild_join_request)를 이 클래스 한곳에서 지킨다.
 * <p>
 * 메서드에 @Transactional을 붙이지 않는다. GuildCommandService가 ProgressWriter.run(매번 새 트랜잭션) 안에서 부르고,
 * 낙관적 락 충돌·교착이면 ProgressWriter가 새 트랜잭션으로 처음부터 다시 부른다(같은 트랜잭션에서 다시 하면 스냅숏이 그대로라 계속 실패).
 * 그래서 각 메서드는 다시 실행돼도 같은 결과가 나와야 하고, 판정에 걸리면 아무것도 저장하지 않고 예외를 던진다(롤백).
 * <p>
 * 순서가 중요한 쓰기는 saveAndFlush로 바로 SQL을 보낸다. Hibernate는 flush 때 INSERT → UPDATE → DELETE 순으로 모아 보내
 * 코드 순서와 실제 잠금 순서가 달라질 수 있기 때문이다(P-2).
 */
@Component
@RequiredArgsConstructor
public class GuildMembershipTx {

    private final GuildRepository guildRepository;
    private final AccountGuildRepository accountGuildRepository;
    private final GuildJoinRequestRepository joinRequestRepository;
    private final GuildCounterRepository counterRepository;
    private final AccountGuildReader accountGuildReader;
    private final AccountService accountService;
    private final GuildProperties properties;

    /**
     * G3 만들기 본문. 만들기 조건(스테이지 클리어)은 부르기 전에 GuildCommandService가 본다.
     * 같은 이름을 동시에 만들면 둘째의 guild INSERT가 유일 제약(uk_guild_name)에 걸린다 → 부르는 쪽이 409로 바꾼다.
     *
     * @return 만든 길드 ID
     */
    public Long create(Long accountId, String name, Instant now) {
        AccountGuild me = accountGuildReader.getOrCreate(accountId);
        if (me.isInGuild()) {
            throw GuildErrors.alreadyJoined();
        }
        if (guildRepository.existsByName(name)) {
            throw GuildErrors.nameDuplicated();
        }
        GuildCounter counter = counterRepository.getSingle();
        if (!counter.canCreate(properties.maxGuilds())) {
            throw GuildErrors.limitReached();
        }

        // 잠금 순서 1: guild_counter 행 version을 먼저 올린다(동시 만들기가 여기서 부딪혀 하나씩 통과)
        counter.increase();
        counterRepository.saveAndFlush(counter);
        // 2: 새 guild 행(유일 제약이 이름 중복의 최종 방어)
        Guild guild = guildRepository.saveAndFlush(Guild.create(name, accountId, properties.maxMembers()));
        // 3: 내 account_guild 행 = 길드장(version 올림 — 그사이 다른 길드에 들어갔으면 낙관적 락 충돌 → 다시 하면 409)
        me.join(guild.getId(), GuildRole.LEADER, now);
        accountGuildRepository.saveAndFlush(me);
        // 4: 내 대기 중 신청은 모두 지운다(길드장이 되면 신청이 의미 없음)
        joinRequestRepository.deleteAllOfAccount(accountId);
        return guild.getId();
    }

    /**
     * G5 가입 신청 본문. 첫 SQL이 내 account_guild 행 잠금이어야 한다(AccountGuildReader.getOrCreateForApply 설명).
     * 잠근 뒤의 일반 SELECT는 앞선 신청이 커밋한 행까지 보므로 "대기 중 N건" 개수가 정확하다.
     * 그사이 길드가 해체되면 INSERT가 외래 키(fk_guild_join_request_guild)에 걸린다 → 부르는 쪽이 404로 바꾼다.
     */
    public GuildJoinRequestResponse apply(Long accountId, Long guildId, Instant now) {
        AccountGuild me = accountGuildReader.getOrCreateForApply(accountId);
        if (me.isInGuild()) {
            throw GuildErrors.alreadyJoined();
        }
        Guild guild = guildRepository.findById(guildId).orElseThrow(GuildErrors::notFound);

        Duration ttl = properties.joinRequestTtl();
        List<GuildJoinRequest> mine = joinRequestRepository.findAllByAccountIdOrderByIdAsc(accountId);
        Optional<GuildJoinRequest> same = mine.stream().filter(r -> r.getGuildId().equals(guildId)).findFirst();
        if (same.isPresent()) {
            if (!same.get().isExpired(now, ttl)) {
                throw GuildErrors.joinRequestDuplicated();
            }
            // 만료된 같은 길드 신청은 지우고 새로 받는다(유일 제약 (account_id, guild_id) 때문에 먼저 지워야 함)
            joinRequestRepository.delete(same.get());
            joinRequestRepository.flush();
        }
        long pending = mine.stream()
                .filter(r -> !r.getGuildId().equals(guildId))
                .filter(r -> !r.isExpired(now, ttl))
                .count();
        if (pending >= properties.maxPendingRequests()) {
            throw GuildErrors.joinRequestLimit(properties.maxPendingRequests());
        }
        // 안내용 정원 검사. 진짜 판정은 수락 때(guild 행 version + CHECK)
        if (guild.isFull()) {
            throw GuildErrors.full();
        }

        GuildJoinRequest saved = joinRequestRepository.saveAndFlush(GuildJoinRequest.application(guildId, accountId));
        String nickname = accountService.getNicknames(Set.of(accountId)).get(accountId);
        return GuildJoinRequestResponse.from(saved, guild.getName(), nickname, ttl);
    }

}
