package com.pw01.webserver.run.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.account.service.StageRewardResult;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.config.RunProperties;
import com.pw01.webserver.run.dto.ResultSubmitRequest;
import com.pw01.webserver.run.dto.ResultSubmitResponse;
import com.pw01.webserver.run.dto.RunResultResponse;
import com.pw01.webserver.run.entity.RunResult;
import com.pw01.webserver.run.repository.RunResultRepository;
import com.pw01.webserver.stage.service.StageProgressService;
import com.pw01.webserver.stage.service.StageWaveRule;
import com.pw01.webserver.stageplay.entity.StagePlay;
import com.pw01.webserver.stageplay.repository.StagePlayRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * R2 결과 제출(result-api.md): 판 확인 → 이미 결과가 있으면 처음 결과 → 검사 → 보상·결과·진행을 한 트랜잭션으로 저장.
 * 이 클래스는 트랜잭션을 직접 열고(TransactionTemplate), 낙관적 락 충돌이면 **새 트랜잭션으로** 처음부터 다시 한다
 * (같은 트랜잭션 안에서 다시 하면 계속 실패한다). 정한 횟수를 넘으면 409 VERSION_CONFLICT(retryable true).
 * 같은 판의 결과가 동시에 둘이면 뒤 트랜잭션은 run_result 기본 키 위반으로 전부 되돌아가고, 다시 할 때 처음 결과를 받는다.
 */
@Service
@RequiredArgsConstructor
public class RunResultService {

    private static final Logger log = LoggerFactory.getLogger(RunResultService.class);
    private static final Pattern RUN_ID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final StagePlayRepository stagePlayRepository;
    private final RunResultRepository runResultRepository;
    private final AccountService accountService;
    private final StageProgressService stageProgressService;
    private final RunProperties runProperties;
    private final TransactionTemplate transactionTemplate;

    public ResultSubmitResponse submit(Long accountId, String pathRunId, ResultSubmitRequest request) {
        String runId = normalizeRunId(pathRunId);
        String requestId = request.requestId().toLowerCase(Locale.ROOT);

        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> submitOnce(accountId, runId, requestId, request));
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                // 같은 계정의 다른 저장과 부딪힘(진행 행 @Version) 또는 같은 판 결과가 먼저 저장됨(기본 키).
                // 둘 다 새 트랜잭션으로 다시 하면 풀린다(뒤의 경우는 2번에서 처음 결과를 받음).
                // 그 밖의 제약 위반(CHECK 등)은 다시 해도 같으므로 그대로 던진다(500)
                if (e instanceof DataIntegrityViolationException && !resultSaved(runId)) {
                    throw e;
                }
                if (attempt >= runProperties.saveAttempts()) {
                    log.warn("결과 저장 충돌이 계속됨 runId={} attempts={}", runId, attempt, e);
                    throw RunErrors.versionConflict();
                }
                log.info("결과 저장 충돌, 다시 함 runId={} attempt={} cause={}", runId, attempt,
                        e.getClass().getSimpleName());
            }
        }
    }

    private ResultSubmitResponse submitOnce(Long accountId, String runId, String requestId,
                                            ResultSubmitRequest request) {
        // 1. 내 판인가(남의 판도 없는 것과 같음)
        StagePlay run = stagePlayRepository.findByIdAndAccountId(runId, accountId).orElseThrow(RunErrors::runNotFound);

        // 2. 이미 결과가 있으면 처음 결과를 돌려준다(판 번호가 이김). 아래 검사·저장·recordResult를 지나지 않는다
        Optional<RunResult> existing = runResultRepository.findById(runId);
        if (existing.isPresent()) {
            log.info("이미 저장된 결과를 돌려줌 runId={} requestId={} firstRequestId={}", runId, requestId,
                    existing.get().getRequestId());
            return new ResultSubmitResponse(RunResultResponse.from(existing.get()),
                    accountService.getSnapshot(accountId));
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        validate(run, request, now);

        // 3. 보상(경험치·레벨·스탯 포인트) → 변경 내역·누적 통계까지 AccountService가 같은 트랜잭션에서
        boolean cleared = request.cleared();
        StageRewardResult reward = accountService.grantStageReward(accountId, run.getId(), requestId, cleared,
                request.killCount(), request.earnedGold());

        // 4. 결과 한 줄(기본 키 = 판 번호). 바로 flush해서 같은 판의 동시 저장을 여기서 드러낸다
        long playTimeMs = Math.round(request.playTimeSeconds() * 1000);
        RunResult result = runResultRepository.saveAndFlush(RunResult.of(run, requestId, cleared,
                request.reachedWave(), request.totalWaveCount(), playTimeMs, request.earnedGold(),
                request.killCount(), reward.gain()));
        // d(스테이지 플레이): 플레이를 CLEARED·FAILED로 끝낸다. 포기로 이미 끝난 플레이면 409 STAGE_PLAY_NOT_IN_PROGRESS로
        // 이 트랜잭션 전체(보상·결과 포함)가 되돌아간다(질문서 A2·A3 추천안, 회의 전 가정)
        run.endWithResult(cleared, now);

        // 5. d의 줄(stage-lines-for-c-1008.md 4장): 같은 트랜잭션에서 클리어 기록. 스테이지·판 번호는 판 행 값
        boolean firstClear = stageProgressService.recordResult(accountId, run.getStageId(), cleared, run.getId());
        log.info("결과 저장 runId={} stage={} cleared={} exp={} level {}→{} firstClear={}", run.getId(),
                run.getStageId(), cleared, reward.gain().expGained(), reward.gain().levelBefore(),
                reward.gain().levelAfter(), firstClear);

        return new ResultSubmitResponse(RunResultResponse.from(result), reward.account());
    }

    /** result-api.md "검사" 3~6. 걸리면 저장하지 않고 거절(retryable false) */
    private void validate(StagePlay run, ResultSubmitRequest request, Instant now) {
        if (run.isExpiredAt(now)) {
            throw RunErrors.runExpired();
        }

        List<FieldErrorDetail> mismatches = new ArrayList<>();
        if (!run.getStageId().equals(request.stageId())) {
            mismatches.add(new FieldErrorDetail("stageId", "판 시작 때의 스테이지(" + run.getStageId() + ")와 다릅니다."));
        }
        if (run.getDifficulty() != request.difficultyOrZero()) {
            mismatches.add(new FieldErrorDetail("difficulty", "판 시작 때의 난이도(" + run.getDifficulty() + ")와 다릅니다."));
        }
        if (!mismatches.isEmpty()) {
            throw RunErrors.mismatch(mismatches);
        }

        // d의 줄: 웨이브 기준은 판에 저장한 waveCount(요청의 totalWaveCount는 검사에 쓰지 않음)
        if (!StageWaveRule.isValidReach(run.getWaveCount(), request.cleared(), request.reachedWave())) {
            throw RunErrors.invalid("reachedWave", request.cleared()
                    ? "클리어면 도달 웨이브가 판의 웨이브 수(" + run.getWaveCount() + ")와 같아야 합니다."
                    : "도달 웨이브는 0 이상 판의 웨이브 수(" + run.getWaveCount() + ") 이하여야 합니다.");
        }
        if (request.totalWaveCount() != run.getWaveCount()) {
            log.warn("게임 웨이브 수가 판과 다름 runId={} game={} run={}", run.getId(), request.totalWaveCount(),
                    run.getWaveCount());
        }

        double playTime = request.playTimeSeconds();
        Duration elapsed = Duration.between(run.getStartedAt(), now).plus(runProperties.playTimeSlack());
        if (playTime < 0 || playTime > runProperties.playTimeMax().toSeconds()
                || playTime > elapsed.toMillis() / 1000.0) {
            log.warn("플레이 시간 거절 runId={} playTime={} elapsedWithSlack={}s", run.getId(), playTime,
                    elapsed.toSeconds());
            throw RunErrors.invalid("playTimeSeconds", "플레이 시간이 판 시작 뒤 지난 시간보다 길거나 상한("
                    + runProperties.playTimeMax().toSeconds() + "초)을 넘습니다.");
        }
    }

    private boolean resultSaved(String runId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> runResultRepository.existsById(runId)));
    }

    /** 경로의 판 번호를 소문자로 맞춘다. UUID 모양이 아니면 400 VALIDATION_FAILED(runId) */
    static String normalizeRunId(String value) {
        if (value == null || !RUN_ID.matcher(value).matches()) {
            throw RunErrors.badRunId();
        }
        return value.toLowerCase(Locale.ROOT);
    }

}
