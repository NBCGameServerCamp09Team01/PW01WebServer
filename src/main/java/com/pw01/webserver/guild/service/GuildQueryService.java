package com.pw01.webserver.guild.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.config.GuildProperties;
import com.pw01.webserver.guild.dto.GuildCreateStatusResponse;
import com.pw01.webserver.guild.dto.GuildDetailResponse;
import com.pw01.webserver.guild.dto.GuildJoinRequestResponse;
import com.pw01.webserver.guild.dto.GuildListResponse;
import com.pw01.webserver.guild.dto.GuildMemberResponse;
import com.pw01.webserver.guild.dto.GuildRequirementResponse;
import com.pw01.webserver.guild.dto.GuildSummaryResponse;
import com.pw01.webserver.guild.dto.MyGuildInfoResponse;
import com.pw01.webserver.guild.dto.MyGuildResponse;
import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.entity.Guild;
import com.pw01.webserver.guild.entity.GuildJoinRequest;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import com.pw01.webserver.guild.repository.GuildJoinRequestRepository;
import com.pw01.webserver.guild.repository.GuildRepository;
import com.pw01.webserver.presence.service.PresenceService;
import com.pw01.webserver.stage.service.StageProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 길드 조회(guild-api.md G1·G2·G4). 읽기 전용 트랜잭션 하나에서 읽어, 한 응답 안의 값들이 같은 시점(스냅숏)이다.
 * 닉네임은 계정 기능(AccountService), 클리어 여부는 스테이지 기능(StageProgressService), 접속 여부는 presence가 답한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuildQueryService {

    /** 이름 검색어: 길드 이름에 쓸 수 있는 글자만(앞 공백 불가), 1~12자. %·_가 들어올 수 없어 LIKE 이스케이프가 필요 없다 */
    private static final Pattern NAME_PREFIX = Pattern.compile("[가-힣A-Za-z0-9][가-힣A-Za-z0-9 ]{0,11}");

    private final GuildRepository guildRepository;
    private final AccountGuildRepository accountGuildRepository;
    private final GuildJoinRequestRepository joinRequestRepository;
    private final AccountGuildReader accountGuildReader;
    private final AccountService accountService;
    private final StageProgressService stageProgressService;
    private final PresenceService presenceService;
    private final GuildProperties properties;

    /**
     * G1 목록. name이 비어 있으면(null·"") 전부. cursor는 앞 응답의 nextCursor 그대로.
     * 묶음 크기보다 하나 더 읽어 다음 묶음이 있는지 안다.
     */
    public GuildListResponse list(String name, String cursor, Integer size) {
        String prefix = namePrefix(name);
        Long after = cursor(cursor);
        int limit = size(size);

        List<Guild> rows = guildRepository.findPage(after, prefix, Limit.of(limit + 1));
        boolean hasNext = rows.size() > limit;
        List<Guild> page = hasNext ? rows.subList(0, limit) : rows;

        Map<Long, String> nicknames = accountService.getNicknames(
                page.stream().map(Guild::getLeaderAccountId).collect(Collectors.toSet()));
        List<GuildSummaryResponse> guilds = page.stream()
                .map(guild -> GuildSummaryResponse.from(guild, nicknames.get(guild.getLeaderAccountId())))
                .toList();
        String nextCursor = hasNext ? page.getLast().getId().toString() : null;
        return new GuildListResponse(guilds, hasNext, nextCursor);
    }

    /** G2 길드 하나. 숫자가 아닌 ID는 400, 없는(해체된) 길드는 404 */
    public GuildDetailResponse get(String guildId) {
        Long id = GuildIds.parse(guildId)
                .orElseThrow(() -> GuildErrors.invalidValue("guildId", "길드 ID가 올바르지 않습니다."));
        Guild guild = guildRepository.findById(id).orElseThrow(GuildErrors::notFound);
        String leaderNickname = accountService.getNicknames(Set.of(guild.getLeaderAccountId()))
                .get(guild.getLeaderAccountId());
        return new GuildDetailResponse(GuildSummaryResponse.from(guild, leaderNickname));
    }

    /**
     * G4 내 길드 화면 전체. 늘 200. account_guild 행이 없는 계정(혼재 배포)은 무소속으로 본다(읽기 전용이라 만들지 않음).
     */
    public MyGuildResponse myGuild(Long accountId) {
        Instant now = Instant.now();
        Duration ttl = properties.joinRequestTtl();
        String requiredStage = properties.createRequiredStage();
        boolean cleared = stageProgressService.hasCleared(accountId, requiredStage);
        List<GuildRequirementResponse> requirements = List.of(GuildRequirementResponse.stageCleared(requiredStage, cleared));

        Optional<AccountGuild> me = accountGuildReader.find(accountId).filter(AccountGuild::isInGuild);
        if (me.isPresent()) {
            return memberView(me.get(), now, ttl, requirements);
        }
        return unaffiliatedView(accountId, now, ttl, cleared, requirements);
    }

    private MyGuildResponse memberView(AccountGuild me, Instant now, Duration ttl,
                                       List<GuildRequirementResponse> requirements) {
        // account_guild.guild_id는 FK라 가리키는 guild 행이 반드시 있다. 없으면 데이터 버그(500)
        Guild guild = guildRepository.findById(me.getGuildId())
                .orElseThrow(() -> new IllegalStateException("account_guild가 없는 guild를 가리킴: " + me.getGuildId()));

        // 길드장 먼저, 그다음 들어온 순(리포지토리가 joined_at 순으로 주므로 안정 정렬로 길드장만 앞으로)
        List<AccountGuild> members = accountGuildRepository.findAllByGuildIdOrderByJoinedAtAsc(guild.getId()).stream()
                .sorted(Comparator.comparing(member -> !member.isLeader()))
                .toList();
        List<Long> memberIds = members.stream().map(AccountGuild::getAccountId).toList();
        Map<Long, String> nicknames = accountService.getNicknames(memberIds);
        Set<Long> online = presenceService.onlineOf(memberIds);

        List<GuildMemberResponse> memberResponses = members.stream()
                .map(member -> GuildMemberResponse.from(member, nicknames.get(member.getAccountId()),
                        online.contains(member.getAccountId())))
                .toList();
        long pending = me.isLeader()
                ? joinRequestRepository.countByGuildIdAndCreatedAtAfter(guild.getId(), now.minus(ttl))
                : 0;
        GuildSummaryResponse summary = GuildSummaryResponse.from(guild, nicknames.get(guild.getLeaderAccountId()));
        return new MyGuildResponse(MyGuildInfoResponse.of(summary, me.getRole()), memberResponses, (int) pending,
                List.of(), new GuildCreateStatusResponse(false, requirements), null);
    }

    private MyGuildResponse unaffiliatedView(Long accountId, Instant now, Duration ttl, boolean cleared,
                                             List<GuildRequirementResponse> requirements) {
        List<GuildJoinRequest> requests = joinRequestRepository.findAllByAccountIdOrderByIdAsc(accountId).stream()
                .filter(request -> !request.isExpired(now, ttl))
                .toList();
        Map<Long, String> guildNames = guildRepository.findAllById(
                        requests.stream().map(GuildJoinRequest::getGuildId).toList()).stream()
                .collect(Collectors.toMap(Guild::getId, Guild::getName));
        String myNickname = accountService.getNicknames(Set.of(accountId)).get(accountId);

        List<GuildJoinRequestResponse> myRequests = requests.stream()
                .map(request -> GuildJoinRequestResponse.from(request, guildNames.get(request.getGuildId()),
                        myNickname, ttl))
                .toList();
        return new MyGuildResponse(null, List.of(), 0, myRequests,
                new GuildCreateStatusResponse(cleared, requirements), null);
    }

    private static String namePrefix(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (!NAME_PREFIX.matcher(name).matches()) {
            throw GuildErrors.invalidValue("name", "검색어는 한글·영문·숫자 1~12자여야 합니다.");
        }
        return name;
    }

    private static Long cursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        return GuildIds.parse(cursor)
                .orElseThrow(() -> GuildErrors.invalidValue("cursor", "목록 위치가 올바르지 않습니다."));
    }

    private int size(Integer size) {
        if (size == null) {
            return properties.list().defaultSize();
        }
        int max = properties.list().maxSize();
        if (size < 1 || size > max) {
            throw GuildErrors.invalidValue("size", "size는 1~" + max + "이어야 합니다.");
        }
        return size;
    }

}
