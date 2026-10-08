package com.pw01.webserver.run.service;

import com.pw01.webserver.config.RunProperties;
import com.pw01.webserver.run.dto.RunStartRequest;
import com.pw01.webserver.run.dto.RunStartResponse;
import com.pw01.webserver.run.entity.Run;
import com.pw01.webserver.run.repository.RunRepository;
import com.pw01.webserver.stage.service.StageDef;
import com.pw01.webserver.stage.service.StageProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * R1 판 시작(result-api.md). 스테이지 확인 → 판 번호 발급 → 그 스테이지와 그때의 웨이브 수를 판에 저장.
 * 같은 requestId로 다시 오면(응답을 못 받음) 처음 판을 그대로 돌려준다(uk_run_account_start_request).
 */
@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;
    private final StageProgressService stageProgressService;
    private final RunProperties runProperties;

    @Transactional
    public RunStartResponse start(Long accountId, RunStartRequest request) {
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        var existing = runRepository.findByAccountIdAndStartRequestId(accountId, requestId);
        if (existing.isPresent()) {
            return RunStartResponse.from(existing.get());
        }

        // d의 줄(stage-lines-for-c-1008.md 3장): 판 번호를 발급하기 전에 부른다. 404 STAGE_NOT_FOUND / 403 STAGE_LOCKED
        StageDef stage = stageProgressService.requireStartable(accountId, request.stageId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Run run = runRepository.save(Run.issue(accountId, stage, request.difficultyOrZero(), requestId, now,
                runProperties.validity()));
        return RunStartResponse.from(run);
    }

}
