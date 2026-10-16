package com.pw01.webserver.guild.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.common.chat.GuildDisbandedEvent;
import com.pw01.webserver.common.chat.GuildMemberJoinedEvent;
import com.pw01.webserver.common.chat.GuildMemberLeftEvent;
import com.pw01.webserver.config.GuildProperties;
import com.pw01.webserver.guild.dto.GuildApproveResponse;
import com.pw01.webserver.guild.dto.GuildDisbandResponse;
import com.pw01.webserver.guild.dto.GuildJoinRequestResponse;
import com.pw01.webserver.guild.dto.GuildLeaveResponse;
import com.pw01.webserver.guild.dto.GuildMemberResponse;
import com.pw01.webserver.guild.dto.GuildRejectResponse;
import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.entity.Guild;
import com.pw01.webserver.guild.entity.GuildCounter;
import com.pw01.webserver.guild.entity.GuildJoinRequest;
import com.pw01.webserver.guild.entity.GuildRole;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import com.pw01.webserver.guild.repository.GuildCounterRepository;
import com.pw01.webserver.guild.repository.GuildJoinRequestRepository;
import com.pw01.webserver.guild.repository.GuildRepository;
import com.pw01.webserver.presence.service.PresenceService;
import com.pw01.webserver.realtime.dto.RealtimeMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final PresenceService presenceService;
    private final ApplicationEventPublisher events;
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

        // 길드장에게 GUILD_JOIN_REQUESTED(받은 유효 신청 수 포함 — 방금 넣은 행도 셈)
        long pendingCount = joinRequestRepository.countByGuildIdAndCreatedAtAfter(guildId, now.minus(ttl));
        notice(guildId, List.of(guild.getLeaderAccountId()), RealtimeMessageType.GUILD_JOIN_REQUESTED, data(
                "guildId", guildId.toString(),
                "joinRequestId", saved.getId().toString(),
                "accountId", accountId.toString(),
                "nickname", nickname,
                "pendingCount", pendingCount));
        return GuildJoinRequestResponse.from(saved, guild.getName(), nickname, ttl);
    }

    /**
     * G8 수락 본문. 길드원을 넣는 길은 이 메서드 하나다(나중의 "초대 수락"도 여기로 — 정원·1인 1길드·잠금 순서가 한곳에).
     * <ol>
     *   <li>guild 행 인원 +1을 **먼저 saveAndFlush**: version UPDATE로 guild 행 배타 잠금을 먼저 잡는다.
     *       뒤에서 account_guild.guild_id를 바꾸면 InnoDB가 외래 키 검사로 guild 행에 공유 잠금을 거는데,
     *       두 수락이 공유 잠금을 쥔 채 배타 잠금으로 올리려 하면 교착한다(P-1). 같은 길드의 수락은 여기서 차례로 선다.</li>
     *   <li>신청이 이 길드 것이고 만료 전인지 확인 → 아니면 404</li>
     *   <li>신청자 account_guild 행 = MEMBER(version 올림 — 그사이 다른 길드에 들어갔으면 충돌 → 다시 하면 409)</li>
     *   <li>신청자의 대기 중 신청 모두 삭제, 길드 생애 이벤트 발행(받는 쪽은 커밋 뒤)</li>
     * </ol>
     */
    public GuildApproveResponse approve(Long leaderId, Long joinRequestId, Instant now) {
        Guild guild = requireLeaderGuild(leaderId);
        if (guild.isFull()) {
            throw GuildErrors.full();
        }
        guild.increaseMemberCount();
        guildRepository.saveAndFlush(guild);

        GuildJoinRequest request = joinRequestRepository.findByIdAndGuildId(joinRequestId, guild.getId())
                .filter(r -> !r.isExpired(now, properties.joinRequestTtl()))
                .orElseThrow(GuildErrors::joinRequestNotFound);
        AccountGuild applicant = accountGuildReader.getOrCreate(request.getAccountId());
        if (applicant.isInGuild()) {
            throw GuildErrors.alreadyJoined();
        }
        applicant.join(guild.getId(), GuildRole.MEMBER, now);
        accountGuildRepository.saveAndFlush(applicant);
        joinRequestRepository.deleteAllOfAccount(applicant.getAccountId());

        events.publishEvent(new GuildMemberJoinedEvent(guild.getId().toString(), applicant.getAccountId()));
        String nickname = accountService.getNicknames(Set.of(applicant.getAccountId())).get(applicant.getAccountId());

        // 길드원 전부(새 사람 포함 — 이 트랜잭션의 쓰기는 자기 SELECT에 보인다)에게 GUILD_MEMBER_JOINED
        notice(guild.getId(), accountGuildRepository.findAccountIdsByGuildId(guild.getId()),
                RealtimeMessageType.GUILD_MEMBER_JOINED, data(
                        "guildId", guild.getId().toString(),
                        "accountId", applicant.getAccountId().toString(),
                        "nickname", nickname,
                        "memberCount", guild.getMemberCount()));
        boolean online = presenceService.onlineOf(Set.of(applicant.getAccountId())).contains(applicant.getAccountId());
        return new GuildApproveResponse(GuildMemberResponse.from(applicant, nickname, online), guild.getMemberCount());
    }

    /** G9 거절 본문. guild_join_request 행 하나만 지운다(guild 행은 고치지 않음) */
    public GuildRejectResponse reject(Long leaderId, Long joinRequestId, Instant now) {
        Guild guild = requireLeaderGuild(leaderId);
        GuildJoinRequest request = joinRequestRepository.findByIdAndGuildId(joinRequestId, guild.getId())
                .filter(r -> !r.isExpired(now, properties.joinRequestTtl()))
                .orElseThrow(GuildErrors::joinRequestNotFound);
        joinRequestRepository.delete(request);
        joinRequestRepository.flush();

        // 신청자에게 GUILD_JOIN_REJECTED(신청자는 길드원이 아니지만 길드에 관한 메시지라 그 길드 채널로)
        notice(guild.getId(), List.of(request.getAccountId()), RealtimeMessageType.GUILD_JOIN_REJECTED, data(
                "guildId", guild.getId().toString(),
                "guildName", guild.getName(),
                "joinRequestId", joinRequestId.toString()));
        return new GuildRejectResponse(joinRequestId.toString());
    }

    /**
     * G11 탈퇴 본문. 길드장이면 해체 길(혼자일 때만, 길드원이 있으면 409). 길드원이면 guild 행 인원 −1을 먼저 flush한 뒤
     * 내 account_guild 행을 무소속으로(수락과 같은 잠금 순서).
     */
    public GuildLeaveResponse leave(Long accountId, Instant now) {
        AccountGuild me = accountGuildReader.getOrCreate(accountId);
        if (!me.isInGuild()) {
            throw GuildErrors.notMember();
        }
        Long guildId = me.getGuildId();
        if (me.isLeader()) {
            disbandGuild(guildId, accountId, true, now);
            return new GuildLeaveResponse(guildId.toString(), true);
        }

        Guild guild = guildRepository.findById(guildId).orElseThrow(GuildErrors::notMember);
        guild.decreaseMemberCount();
        guildRepository.saveAndFlush(guild);
        me.leave();
        accountGuildRepository.saveAndFlush(me);
        events.publishEvent(new GuildMemberLeftEvent(guildId.toString(), accountId, GuildMemberLeftEvent.Reason.LEFT));

        // 남은 길드원 + 나간 사람(내 화면도 길드 탭을 비워야 함)에게 GUILD_MEMBER_LEFT
        List<Long> recipients = new ArrayList<>(accountGuildRepository.findAccountIdsByGuildId(guildId));
        recipients.add(accountId);
        notice(guildId, recipients, RealtimeMessageType.GUILD_MEMBER_LEFT, data(
                "guildId", guildId.toString(),
                "accountId", accountId.toString(),
                "memberCount", guild.getMemberCount(),
                "reason", GuildMemberLeftEvent.Reason.LEFT.name()));
        return new GuildLeaveResponse(guildId.toString(), false);
    }

    /** G12 해체 본문 */
    public GuildDisbandResponse disband(Long accountId, Instant now) {
        AccountGuild me = accountGuildReader.getOrCreate(accountId);
        if (!me.isInGuild()) {
            throw GuildErrors.notMember();
        }
        if (!me.isLeader()) {
            throw GuildErrors.notLeader();
        }
        Long guildId = me.getGuildId();
        disbandGuild(guildId, accountId, false, now);
        return new GuildDisbandResponse(guildId.toString());
    }

    /**
     * 해체. 비관적 잠금을 쓰는 유일한 길(설계 5-4).
     * <ol>
     *   <li>guild 행 SELECT … FOR UPDATE를 맨 먼저: 잠금 읽기라 최신 값(길드장·인원)을 보고, 지우는 동안 수락·탈퇴가 끼지 못한다
     *       (그쪽의 guild 행 version UPDATE가 여기서 기다렸다가, 해체가 커밋되면 0행으로 실패해 다시 한 뒤 403·404).</li>
     *   <li>해체 직전 길드원 ID를 잠금 읽기로(일반 SELECT는 스냅숏이라 방금 수락된 길드원을 놓칠 수 있음)</li>
     *   <li>벌크: account_guild 소속 비우기(version +1) → guild_join_request 삭제 → guild 삭제. 외래 키 때문에 이 순서여야 한다.
     *       벌크 뒤에는 영속성 컨텍스트가 비워지므로 앞서 읽은 엔티티를 다시 쓰지 않는다(P-3)</li>
     *   <li>guild_counter −1(잠금 순서 예외: 맨 뒤. 생성이 잡는 guild 행은 새 행이라 원이 생기지 않음)</li>
     *   <li>GuildDisbandedEvent 발행 — 채팅 모듈이 커밋 뒤 받아 그 길드 채팅을 지운다</li>
     * </ol>
     *
     * @param leaving 길드장 탈퇴에서 왔으면 true(다른 길드원이 있으면 409 GUILD_LEADER_CANNOT_LEAVE)
     */
    private void disbandGuild(Long guildId, Long leaderId, boolean leaving, Instant now) {
        Guild guild = guildRepository.findByIdForUpdate(guildId).orElseThrow(GuildErrors::notMember);
        if (!guild.isLeader(leaderId)) {
            throw GuildErrors.notLeader();
        }
        if (leaving && guild.getMemberCount() > 1) {
            throw GuildErrors.leaderCannotLeave();
        }
        List<Long> formerMembers = accountGuildRepository.lockAccountIdsByGuildId(guildId);
        List<Long> applicants = joinRequestRepository.lockAccountIdsByGuildId(guildId);

        accountGuildRepository.clearGuild(guildId, now);
        joinRequestRepository.deleteAllOfGuild(guildId);
        guildRepository.deleteGuild(guildId);

        GuildCounter counter = counterRepository.getSingle();
        counter.decrease();
        counterRepository.saveAndFlush(counter);

        events.publishEvent(new GuildDisbandedEvent(guildId.toString(), formerMembers));

        // 해체 직전 길드원 + 그 길드 신청자(내 신청 목록에서 빠져야 함)에게 GUILD_DISBANDED
        List<Long> recipients = new ArrayList<>(formerMembers);
        recipients.addAll(applicants);
        notice(guildId, recipients, RealtimeMessageType.GUILD_DISBANDED, data("guildId", guildId.toString()));
    }

    /** 커밋되면 보낼 실시간 알림을 낸다(GuildNotifier가 AFTER_COMMIT에 받음). 받는 사람·data는 지금 트랜잭션의 값 */
    private void notice(Long guildId, List<Long> recipients, RealtimeMessageType type, Map<String, Object> data) {
        events.publishEvent(new GuildRealtimeNotice(guildId.toString(), recipients, type, data));
    }

    /** 키·값을 번갈아 받아 넣은 순서대로의 data(JSON 필드 순서 = 명세 순서) */
    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            data.put((String) keyValues[i], keyValues[i + 1]);
        }
        return data;
    }

    /** 길드장 기능의 공통 확인: 길드가 없으면 403 NOT_MEMBER, 길드장이 아니면 403 NOT_LEADER */
    private Guild requireLeaderGuild(Long accountId) {
        AccountGuild me = accountGuildReader.getOrCreate(accountId);
        if (!me.isInGuild()) {
            throw GuildErrors.notMember();
        }
        if (!me.isLeader()) {
            throw GuildErrors.notLeader();
        }
        return guildRepository.findById(me.getGuildId()).orElseThrow(GuildErrors::notMember);
    }

}
