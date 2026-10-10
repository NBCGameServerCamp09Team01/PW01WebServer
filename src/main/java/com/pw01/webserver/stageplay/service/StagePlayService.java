package com.pw01.webserver.stageplay.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.config.StagePlayProperties;
import com.pw01.webserver.stage.service.StageDef;
import com.pw01.webserver.stage.service.StageProgressService;
import com.pw01.webserver.stageplay.dto.InProgressStagePlayListResponse;
import com.pw01.webserver.stageplay.dto.StagePlayIds;
import com.pw01.webserver.stageplay.dto.StagePlayResponse;
import com.pw01.webserver.stageplay.dto.StagePlayStartRequest;
import com.pw01.webserver.stageplay.entity.StagePlay;
import com.pw01.webserver.stageplay.entity.StagePlayStatus;
import com.pw01.webserver.stageplay.repository.StagePlayRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 스테이지 플레이 시작·현황(공유 초안 stage-play-api-draft-1009.md P1~P5).
 * 계정당 진행 중은 하나다. 마감이 지난 진행 중 플레이는 따로 도는 정리 작업 없이, 읽거나 새로 시작할 때 EXPIRED로 바꾼다.
 * 결과 제출은 결과 기능(stageresult 패키지)이 맡고, 결과가 저장될 때 StagePlay.endWithResult로 플레이를 끝낸다.
 */
@Service
@RequiredArgsConstructor
public class StagePlayService {

    private static final Logger log = LoggerFactory.getLogger(StagePlayService.class);

    private final StagePlayRepository stagePlayRepository;
    private final StageProgressService stageProgressService;
    private final AccountService accountService;
    private final StagePlayProperties stagePlayProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * P1 시작. 같은 계정의 시작이 동시에 둘 오면 하나는 DB 유일 제약(같은 requestId 또는 진행 중 하나)에 걸린다.
     * 그때는 새 트랜잭션에서 한 번 더 판단한다: 같은 requestId면 처음 플레이, 아니면 409 STAGE_PLAY_IN_PROGRESS.
     */
    public StagePlayResponse start(Long accountId, StagePlayStartRequest request) {
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        try {
            return transactionTemplate.execute(status -> startOnce(accountId, requestId, request));
        } catch (DataIntegrityViolationException e) {
            log.info("스테이지 플레이 시작이 동시에 와서 다시 판단함 accountId={} requestId={} cause={}",
                    accountId, requestId, e.getMostSpecificCause().getMessage());
            return transactionTemplate.execute(status -> startOnce(accountId, requestId, request));
        }
    }

    private StagePlayResponse startOnce(Long accountId, String requestId, StagePlayStartRequest request) {
        // 1. 같은 시작 요청을 다시 보냄(응답을 못 받음) → 처음 플레이. 다른 스테이지면 게임 버그
        Optional<StagePlay> sameRequest = stagePlayRepository.findByAccountIdAndStartRequestId(accountId, requestId);
        if (sameRequest.isPresent()) {
            if (!sameRequest.get().getStageId().equals(request.stageId())) {
                throw StagePlayErrors.requestIdReused();
            }
            return StagePlayResponse.from(sameRequest.get());
        }

        // 2. 진행 중 플레이가 있으면 막는다. 마감이 지났으면 EXPIRED로 끝내고(유일 제약 자리를 비움) 계속
        Instant now = now();
        Optional<StagePlay> current = stagePlayRepository.findByAccountIdAndStatus(accountId, StagePlayStatus.IN_PROGRESS);
        if (current.isPresent()) {
            if (!current.get().expireIfDue(now)) {
                throw StagePlayErrors.inProgress();
            }
            stagePlayRepository.saveAndFlush(current.get());
        }

        // 3. d의 스테이지 확인: 없으면 404 STAGE_NOT_FOUND, 잠겼으면 403 STAGE_LOCKED
        StageDef stage = stageProgressService.requireStartable(accountId, request.stageId());

        // 4. 발급. 바로 flush해서 동시 시작의 유일 제약 위반을 여기서 드러낸다
        StagePlay play = stagePlayRepository.saveAndFlush(StagePlay.start(accountId, stage,
                request.difficultyOrDefault(), requestId, now, stagePlayProperties.validity()));
        log.info("스테이지 플레이 시작 stagePlayId={} accountId={} stage={}", play.getId(), accountId, stage.stageId());
        return StagePlayResponse.from(play);
    }

    /** P2 내 진행 중 플레이. 없으면 빈 값(204). 마감이 지났으면 여기서 EXPIRED로 끝내고 빈 값 */
    @Transactional
    public Optional<StagePlayResponse> getCurrent(Long accountId) {
        return stagePlayRepository.findByAccountIdAndStatus(accountId, StagePlayStatus.IN_PROGRESS)
                .filter(play -> !play.expireIfDue(now()))
                .map(StagePlayResponse::from);
    }

    /** P3 내 플레이 하나. 마감이 지난 진행 중이면 EXPIRED로 바꿔 돌려준다 */
    @Transactional
    public StagePlayResponse get(Long accountId, String pathStagePlayId) {
        StagePlay play = findMine(accountId, pathStagePlayId);
        play.expireIfDue(now());
        return StagePlayResponse.from(play);
    }

    /** P4 포기: 실패로 끝냄(결과·보상 없음). 이미 포기한 플레이면 그대로 200(재전송), 결과·만료로 끝났으면 409 */
    @Transactional
    public StagePlayResponse abandon(Long accountId, String pathStagePlayId) {
        StagePlay play = findMine(accountId, pathStagePlayId);
        Instant now = now();
        if (play.isAbandoned()) {
            return StagePlayResponse.from(play);
        }
        play.expireIfDue(now);
        play.abandon(now);
        log.info("스테이지 플레이 포기 stagePlayId={} accountId={} stage={}", play.getId(), accountId, play.getStageId());
        return StagePlayResponse.from(play);
    }

    /** P5 모든 계정의 진행 중 플레이(마감 전, 최근 시작 순 50개). status는 지금 IN_PROGRESS만 */
    @Transactional(readOnly = true)
    public InProgressStagePlayListResponse listInProgress(String status) {
        if (!StagePlayStatus.IN_PROGRESS.name().equals(status)) {
            throw StagePlayErrors.badStatusFilter();
        }
        List<StagePlay> plays = stagePlayRepository
                .findTop50ByStatusAndExpiresAtAfterOrderByStartedAtDesc(StagePlayStatus.IN_PROGRESS, now());
        return InProgressStagePlayListResponse.from(plays,
                accountService.getNicknames(plays.stream().map(StagePlay::getAccountId).distinct().toList()));
    }

    private StagePlay findMine(Long accountId, String pathStagePlayId) {
        String id = StagePlayIds.normalize(pathStagePlayId).orElseThrow(StagePlayErrors::badId);
        return stagePlayRepository.findByIdAndAccountId(id, accountId).orElseThrow(StagePlayErrors::notFound);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

}
