package com.pw01.webserver.stageplay.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.config.StagePlayProperties;
import com.pw01.webserver.stage.service.StageCatalog;
import com.pw01.webserver.stage.service.StageContent;
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
import org.springframework.context.ApplicationEventPublisher;
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
 * 웨이브 구성(M24, stage-play-api.md "웨이브 구성")은 시작 때 그 난이도(M9)를 반영해 플레이에 고정하고, P1~P4 응답은 모두 그 값을 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class StagePlayService {

    private static final Logger log = LoggerFactory.getLogger(StagePlayService.class);

    private final StagePlayRepository stagePlayRepository;
    private final StageProgressService stageProgressService;
    private final StageCatalog stageCatalog;
    private final WavePlanCodec wavePlanCodec;
    private final AccountService accountService;
    private final StagePlayProperties stagePlayProperties;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * P1 시작. 같은 계정의 시작이 동시에 둘 오면 하나는 DB 유일 제약(같은 requestId 또는 진행 중 하나)에 걸린다.
     * 그때는 새 트랜잭션에서 한 번 더 판단한다: 같은 requestId면 처음 플레이, 아니면 409 STAGE_PLAY_IN_PROGRESS.
     */
    public StagePlayResponse start(Long accountId, StagePlayStartRequest request) {
        // 정의는 실행 중에 바뀔 수 있다(stage-api.md "서버 정의 바꾸기"). 이 요청은 덩어리 하나로 검사·구성·버전을 모두 본다
        StageContent content = stageCatalog.content();
        // 형식 검증 다음: difficulties.json에 있는 난이도만(없으면 400 VALIDATION_FAILED)
        if (!content.hasDifficulty(request.difficultyOrDefault())) {
            throw StagePlayErrors.unknownDifficulty(request.difficultyOrDefault());
        }
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        try {
            return transactionTemplate.execute(status -> startOnce(accountId, requestId, request, content));
        } catch (DataIntegrityViolationException e) {
            log.info("스테이지 플레이 시작이 동시에 와서 다시 판단함 accountId={} requestId={} cause={}",
                    accountId, requestId, e.getMostSpecificCause().getMessage());
            return transactionTemplate.execute(status -> startOnce(accountId, requestId, request, content));
        }
    }

    private StagePlayResponse startOnce(Long accountId, String requestId, StagePlayStartRequest request,
                                        StageContent content) {
        // 1. 같은 시작 요청을 다시 보냄(응답을 못 받음) → 처음 플레이. 다른 스테이지·난이도면 게임 버그
        Optional<StagePlay> sameRequest = stagePlayRepository.findByAccountIdAndStartRequestId(accountId, requestId);
        if (sameRequest.isPresent()) {
            if (!sameRequest.get().getStageId().equals(request.stageId())
                    || sameRequest.get().getDifficulty() != request.difficultyOrDefault()) {
                throw StagePlayErrors.requestIdReused();
            }
            return toResponse(sameRequest.get());
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
        StageDef stage = stageProgressService.requireStartable(accountId, request.stageId(), content);

        // 4. 발급. 웨이브 수와 구성·버전은 같은 정의 덩어리에서 함께 저장한다(체크포인트 범위 = 구성 길이).
        //    구성은 그 난이도의 마리 수·배율을 반영한 것(M9). 바로 flush해서 동시 시작의 유일 제약 위반을 여기서 드러낸다
        int difficulty = request.difficultyOrDefault();
        StagePlay play = stagePlayRepository.saveAndFlush(StagePlay.start(accountId, stage,
                difficulty, requestId, now, stagePlayProperties.validity(),
                wavePlanCodec.write(stage.wavesFor(difficulty)), content.waveVersion()));
        log.info("스테이지 플레이 시작 stagePlayId={} accountId={} stage={} difficulty={} waveVersion={}", play.getId(),
                accountId, stage.stageId(), difficulty, play.getWaveVersion());
        return toResponse(play);
    }

    /** P2 내 진행 중 플레이. 없으면 빈 값(204). 마감이 지났으면 여기서 EXPIRED로 끝내고 빈 값 */
    @Transactional
    public Optional<StagePlayResponse> getCurrent(Long accountId) {
        return stagePlayRepository.findByAccountIdAndStatus(accountId, StagePlayStatus.IN_PROGRESS)
                .filter(play -> !play.expireIfDue(now()))
                .map(this::toResponse);
    }

    /** P3 내 플레이 하나. 마감이 지난 진행 중이면 EXPIRED로 바꿔 돌려준다 */
    @Transactional
    public StagePlayResponse get(Long accountId, String pathStagePlayId) {
        StagePlay play = findMine(accountId, pathStagePlayId);
        play.expireIfDue(now());
        return toResponse(play);
    }

    /** P4 포기: 실패로 끝냄(결과·보상 없음). 이미 포기한 플레이면 그대로 200(재전송), 결과·만료로 끝났으면 409 */
    @Transactional
    public StagePlayResponse abandon(Long accountId, String pathStagePlayId) {
        StagePlay play = findMine(accountId, pathStagePlayId);
        Instant now = now();
        if (play.isAbandoned()) {
            return toResponse(play);
        }
        play.expireIfDue(now);
        play.abandon(now);
        // 커밋 뒤 그 플레이의 체크포인트를 지운다(PlayCheckpointCleaner)
        eventPublisher.publishEvent(new StagePlayEndedEvent(play.getId()));
        log.info("스테이지 플레이 포기 stagePlayId={} accountId={} stage={}", play.getId(), accountId, play.getStageId());
        return toResponse(play);
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

    /**
     * 플레이 → 응답. 웨이브 구성은 시작 때 저장한 값을 쓴다(그 사이 서버 정의가 바뀌어도 그대로).
     * M9 전에 저장한 구성의 적 줄에는 난이도 배율 칸이 없고, 읽을 때 1.0으로 채운다(WaveEnemyDef).
     * V9 전에 시작해 구성이 없는 플레이(모두 난이도 0)는 지금 정의로 채운다. 지금 웨이브 수가 그 플레이의 waveCount와 다르면
     * 체크포인트 범위(waveCount)와 어긋난 구성을 주지 않도록 null로 둔다(게임은 판을 시작하지 않는다).
     */
    private StagePlayResponse toResponse(StagePlay play) {
        if (play.getWavePlan() != null) {
            return StagePlayResponse.from(play, play.getWaveVersion(), wavePlanCodec.read(play.getWavePlan()));
        }
        StageContent content = stageCatalog.content();
        return content.find(play.getStageId())
                .filter(stage -> stage.waves().size() == play.getWaveCount())
                .map(stage -> StagePlayResponse.from(play, content.waveVersion(), stage.waves()))
                .orElseGet(() -> StagePlayResponse.from(play, null, null));
    }

    private StagePlay findMine(Long accountId, String pathStagePlayId) {
        String id = StagePlayIds.normalize(pathStagePlayId).orElseThrow(StagePlayErrors::badId);
        return stagePlayRepository.findByIdAndAccountId(id, accountId).orElseThrow(StagePlayErrors::notFound);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

}
