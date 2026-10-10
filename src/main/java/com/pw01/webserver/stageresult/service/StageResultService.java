package com.pw01.webserver.stageresult.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.account.service.StageRewardResult;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.config.StagePlayProperties;
import com.pw01.webserver.stage.service.StageProgressService;
import com.pw01.webserver.stage.service.StageWaveRule;
import com.pw01.webserver.stageplay.dto.StagePlayIds;
import com.pw01.webserver.stageplay.entity.StagePlay;
import com.pw01.webserver.stageplay.entity.StagePlayStatus;
import com.pw01.webserver.stageplay.repository.StagePlayRepository;
import com.pw01.webserver.stageplay.service.StagePlayErrors;
import com.pw01.webserver.stageresult.dto.ResultSubmitRequest;
import com.pw01.webserver.stageresult.dto.ResultSubmitResponse;
import com.pw01.webserver.stageresult.dto.SaveStatus;
import com.pw01.webserver.stageresult.dto.StageResultResponse;
import com.pw01.webserver.stageresult.entity.StageResult;
import com.pw01.webserver.stageresult.repository.StageResultRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 결과 제출: 내 스테이지 플레이 확인 → 이미 결과가 있으면 처음 결과 → 플레이 상태·결과 검사 → 보상·결과·진행을 한 트랜잭션으로 저장.
 * 이 클래스는 트랜잭션을 직접 열고(TransactionTemplate), 낙관적 락 충돌이면 **새 트랜잭션으로** 처음부터 다시 한다
 * (같은 트랜잭션 안에서 다시 하면 계속 실패한다). 정한 횟수를 넘으면 409 VERSION_CONFLICT(retryable true).
 * 같은 플레이의 결과가 동시에 둘이면 뒤 트랜잭션은 stage_play_result 기본 키 위반으로 전부 되돌아가고, 다시 할 때 처음 결과를 받는다.
 */
@Service
@RequiredArgsConstructor
public class StageResultService {

    private static final Logger log = LoggerFactory.getLogger(StageResultService.class);

    private final StagePlayRepository stagePlayRepository;
    private final StageResultRepository stageResultRepository;
    private final AccountService accountService;
    private final StageProgressService stageProgressService;
    private final StagePlayProperties stagePlayProperties;
    private final TransactionTemplate transactionTemplate;

    public ResultSubmitResponse submit(Long accountId, String pathStagePlayId, ResultSubmitRequest request) {
        String stagePlayId = StagePlayIds.normalize(pathStagePlayId).orElseThrow(StagePlayErrors::badId);
        String requestId = request.requestId().toLowerCase(Locale.ROOT);

        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> submitOnce(accountId, stagePlayId, requestId, request));
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                // 같은 계정의 다른 저장과 부딪힘(진행 행 @Version) 또는 같은 플레이 결과가 먼저 저장됨(기본 키).
                // 둘 다 새 트랜잭션으로 다시 하면 풀린다(뒤의 경우는 2번에서 처음 결과를 받음).
                // 그 밖의 제약 위반(CHECK 등)은 다시 해도 같으므로 그대로 던진다(500)
                if (e instanceof DataIntegrityViolationException && !resultSaved(stagePlayId)) {
                    throw e;
                }
                if (attempt >= stagePlayProperties.saveAttempts()) {
                    log.warn("결과 저장 충돌이 계속됨 stagePlayId={} attempts={}", stagePlayId, attempt, e);
                    throw StageResultErrors.versionConflict();
                }
                log.info("결과 저장 충돌, 다시 함 stagePlayId={} attempt={} cause={}", stagePlayId, attempt,
                        e.getClass().getSimpleName());
            }
        }
    }

    /**
     * 결과 다시 받기: 내 플레이 확인 → 저장된 결과. 게임이 제출 응답을 놓쳤을 때 쓴다.
     * 남의 플레이·없는 플레이는 STAGE_PLAY_NOT_FOUND, 플레이는 있는데 결과가 아직 없으면 STAGE_RESULT_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public StageResultResponse get(Long accountId, String pathStagePlayId) {
        String stagePlayId = StagePlayIds.normalize(pathStagePlayId).orElseThrow(StagePlayErrors::badId);
        stagePlayRepository.findByIdAndAccountId(stagePlayId, accountId).orElseThrow(StagePlayErrors::notFound);
        return stageResultRepository.findById(stagePlayId)
                .map(StageResultResponse::from)
                .orElseThrow(StageResultErrors::resultNotFound);
    }

    private ResultSubmitResponse submitOnce(Long accountId, String stagePlayId, String requestId,
                                            ResultSubmitRequest request) {
        // 1. 내 플레이인가(남의 플레이도 없는 것과 같음)
        StagePlay play = stagePlayRepository.findByIdAndAccountId(stagePlayId, accountId)
                .orElseThrow(StagePlayErrors::notFound);

        // 2. 이미 결과가 있으면 처음 결과를 돌려준다(플레이 ID가 이김). 아래 검사·저장·recordResult를 지나지 않는다
        Optional<StageResult> existing = stageResultRepository.findById(stagePlayId);
        if (existing.isPresent()) {
            log.info("이미 저장된 결과를 돌려줌 stagePlayId={} requestId={} firstRequestId={}", stagePlayId, requestId,
                    existing.get().getRequestId());
            return new ResultSubmitResponse(StageResultResponse.from(existing.get()), SaveStatus.ALREADY_SAVED,
                    accountService.getSnapshot(accountId));
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        validate(play, request, now);

        // 3. 보상(경험치·레벨·스탯 포인트) → 변경 내역·누적 통계까지 AccountService가 같은 트랜잭션에서
        boolean cleared = request.cleared();
        StageRewardResult reward = accountService.grantStageReward(accountId, play.getId(), requestId, cleared,
                request.killCount(), request.earnedGold());

        // 4. 결과 한 줄(기본 키 = 플레이 ID). 바로 flush해서 같은 플레이의 동시 저장을 여기서 드러낸다
        long playTimeMs = Math.round(request.playTimeSeconds() * 1000);
        StageResult result = stageResultRepository.saveAndFlush(StageResult.of(play, requestId, cleared,
                request.reachedWave(), request.totalWaveCount(), playTimeMs, request.earnedGold(),
                request.killCount(), reward.gain()));
        // 플레이를 CLEARED·FAILED로 끝낸다. 진행 중이 아니면 409(마지막 방어, 보통은 검사에서 먼저 걸림)
        play.endWithResult(cleared, now);

        // 5. 같은 트랜잭션에서 클리어 기록. 스테이지·플레이 ID는 플레이 행 값
        boolean firstClear = stageProgressService.recordResult(accountId, play.getStageId(), cleared, play.getId());
        log.info("결과 저장 stagePlayId={} stage={} cleared={} exp={} level {}→{} firstClear={}", play.getId(),
                play.getStageId(), cleared, reward.gain().expGained(), reward.gain().levelBefore(),
                reward.gain().levelAfter(), firstClear);

        return new ResultSubmitResponse(StageResultResponse.from(result), SaveStatus.SAVED, reward.account());
    }

    /** 걸리면 저장하지 않고 거절(retryable false). 플레이 상태를 보상 계산 전에 먼저 본다 */
    private void validate(StagePlay play, ResultSubmitRequest request, Instant now) {
        // 플레이 상태: 만료(저장된 EXPIRED 또는 마감만 지남) → STAGE_PLAY_EXPIRED, 그 밖에 끝남(포기) → NOT_IN_PROGRESS
        if (play.getStatus() == StagePlayStatus.EXPIRED || (play.isInProgress() && play.isExpiredAt(now))) {
            throw StagePlayErrors.expired();
        }
        if (!play.isInProgress()) {
            throw StagePlayErrors.notInProgress();
        }

        List<FieldErrorDetail> mismatches = new ArrayList<>();
        if (!play.getStageId().equals(request.stageId())) {
            mismatches.add(new FieldErrorDetail("stageId", "플레이 시작 때의 스테이지(" + play.getStageId() + ")와 다릅니다."));
        }
        if (play.getDifficulty() != request.difficultyOrZero()) {
            mismatches.add(new FieldErrorDetail("difficulty", "플레이 시작 때의 난이도(" + play.getDifficulty() + ")와 다릅니다."));
        }
        if (!mismatches.isEmpty()) {
            throw StageResultErrors.mismatch(mismatches);
        }

        // 웨이브 기준은 플레이에 저장한 waveCount(요청의 totalWaveCount는 검사에 쓰지 않음)
        if (!StageWaveRule.isValidReach(play.getWaveCount(), request.cleared(), request.reachedWave())) {
            throw StageResultErrors.invalid("reachedWave", request.cleared()
                    ? "클리어면 도달 웨이브가 플레이의 웨이브 수(" + play.getWaveCount() + ")와 같아야 합니다."
                    : "도달 웨이브는 0 이상 플레이의 웨이브 수(" + play.getWaveCount() + ") 이하여야 합니다.");
        }
        if (request.totalWaveCount() != play.getWaveCount()) {
            log.warn("게임 웨이브 수가 플레이와 다름 stagePlayId={} game={} play={}", play.getId(),
                    request.totalWaveCount(), play.getWaveCount());
        }

        double playTime = request.playTimeSeconds();
        Duration elapsed = Duration.between(play.getStartedAt(), now).plus(stagePlayProperties.playTimeSlack());
        if (playTime < 0 || playTime > stagePlayProperties.playTimeMax().toSeconds()
                || playTime > elapsed.toMillis() / 1000.0) {
            log.warn("플레이 시간 거절 stagePlayId={} playTime={} elapsedWithSlack={}s", play.getId(), playTime,
                    elapsed.toSeconds());
            throw StageResultErrors.invalid("playTimeSeconds", "플레이 시간이 시작 뒤 지난 시간보다 길거나 상한("
                    + stagePlayProperties.playTimeMax().toSeconds() + "초)을 넘습니다.");
        }
    }

    private boolean resultSaved(String stagePlayId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                status -> stageResultRepository.existsById(stagePlayId)));
    }

}
