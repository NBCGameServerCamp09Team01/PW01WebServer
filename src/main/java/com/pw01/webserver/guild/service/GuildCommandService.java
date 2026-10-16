package com.pw01.webserver.guild.service;

import com.pw01.webserver.account.service.ProgressWriter;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.idempotency.IdempotencyService;
import com.pw01.webserver.config.GuildProperties;
import com.pw01.webserver.guild.dto.GuildCreateRequest;
import com.pw01.webserver.guild.dto.GuildJoinRequestCreateRequest;
import com.pw01.webserver.guild.dto.GuildJoinRequestResponse;
import com.pw01.webserver.guild.dto.GuildRequirementResponse;
import com.pw01.webserver.guild.dto.MyGuildResponse;
import com.pw01.webserver.guild.repository.GuildJoinRequestRepository;
import com.pw01.webserver.stage.service.StageProgressService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 길드 쓰기(guild-api.md G3·G5·G6, 수락·거절·탈퇴·해체는 W6).
 * 순서: 중복 방지(IdempotencyService) → ProgressWriter(새 트랜잭션, 낙관적 락 충돌·교착이면 다시) → GuildMembershipTx 본문.
 * <p>
 * 이 클래스의 G3·G5 메서드에는 @Transactional을 붙이지 않는다(붙이면 ProgressWriter의 새 트랜잭션이 바깥 트랜잭션에 묶여 다시 하기가 안 됨).
 * 유일·외래 키 위반은 다시 해도 같으므로(ProgressWriter retryOn = false) 여기서 제약 이름을 보고 409·404로 바꾼다(P-6).
 */
@Service
@RequiredArgsConstructor
public class GuildCommandService {

    private static final Logger log = LoggerFactory.getLogger(GuildCommandService.class);

    private static final String UK_GUILD_NAME = "uk_guild_name";
    private static final String UK_JOIN_REQUEST = "uk_guild_join_request_account_guild";
    private static final String FK_JOIN_REQUEST_GUILD = "fk_guild_join_request_guild";

    private final IdempotencyService idempotencyService;
    private final ProgressWriter progressWriter;
    private final GuildMembershipTx membershipTx;
    private final GuildQueryService guildQueryService;
    private final StageProgressService stageProgressService;
    private final GuildJoinRequestRepository joinRequestRepository;
    private final GuildProperties properties;

    /** G3 만들기. 응답은 커밋 뒤 내 길드(G4 모양) */
    public MyGuildResponse create(Long accountId, GuildCreateRequest request) {
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        return idempotencyService.run(accountId, requestId, request, MyGuildResponse.class, () -> {
            requireCreateConditions(accountId);
            try {
                Long guildId = progressWriter.run("guild-create requestId=" + requestId,
                        () -> membershipTx.create(accountId, request.name(), Instant.now()), e -> false);
                log.info("길드 만듦 guildId={} accountId={}", guildId, accountId);
            } catch (DataIntegrityViolationException e) {
                throw translate(e);
            }
            return guildQueryService.myGuild(accountId);
        });
    }

    /** G5 가입 신청 */
    public GuildJoinRequestResponse apply(Long accountId, GuildJoinRequestCreateRequest request) {
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        Long guildId = Long.parseLong(request.guildId());
        return idempotencyService.run(accountId, requestId, request, GuildJoinRequestResponse.class, () -> {
            try {
                return progressWriter.run("guild-apply requestId=" + requestId,
                        () -> membershipTx.apply(accountId, guildId, Instant.now()), e -> false);
            } catch (DataIntegrityViolationException e) {
                throw translate(e);
            }
        });
    }

    /**
     * G6 신청 취소. 내 신청이면 지우고, 없거나 남의 것이어도 그대로 끝낸다(204, 다시 보내도 결과가 같아 requestId가 필요 없음).
     * guild_join_request 행 하나만 지우므로 잠금 순서와 무관하고 다시 하기도 필요 없다.
     */
    @Transactional
    public void cancel(Long accountId, String joinRequestId) {
        Long id = GuildIds.parse(joinRequestId)
                .orElseThrow(() -> GuildErrors.invalidValue("joinRequestId", "신청 ID가 올바르지 않습니다."));
        int deleted = joinRequestRepository.deleteOwn(id, accountId);
        log.info("가입 신청 취소 joinRequestId={} accountId={} deleted={}", id, accountId, deleted);
    }

    /** 만들기 조건: 지금은 스테이지 클리어 하나. 걸리면 409 + requirements(조건 전부) */
    private void requireCreateConditions(Long accountId) {
        String stageId = properties.createRequiredStage();
        boolean cleared = stageProgressService.hasCleared(accountId, stageId);
        if (!cleared) {
            throw new ConflictException(GuildErrors.GUILD_CREATE_REQUIREMENTS_NOT_MET, "길드를 만들 조건을 채우지 못했습니다.",
                    Map.of("requirements", List.of(GuildRequirementResponse.stageCleared(stageId, false))));
        }
    }

    /** 제약 위반 → 명세 오류. 모르는 제약이면 그대로 던진다(500, 데이터 버그) */
    private static RuntimeException translate(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains(UK_GUILD_NAME)) {
            return GuildErrors.nameDuplicated();
        }
        if (message.contains(UK_JOIN_REQUEST)) {
            return GuildErrors.joinRequestDuplicated();
        }
        if (message.contains(FK_JOIN_REQUEST_GUILD)) {
            return GuildErrors.notFound();
        }
        return e;
    }

}
