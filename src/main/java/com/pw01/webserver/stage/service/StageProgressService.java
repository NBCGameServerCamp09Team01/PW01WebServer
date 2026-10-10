package com.pw01.webserver.stage.service;

import com.pw01.webserver.stage.dto.StageProgressListResponse;
import com.pw01.webserver.stage.dto.StageProgressResponse;
import com.pw01.webserver.stage.entity.AccountStageProgress;
import com.pw01.webserver.stage.repository.AccountStageProgressRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 계정의 스테이지 진행: 내 진행 조회(ST2), 판 시작 판정, 클리어 기록.
 * 진행 표에는 클리어 행만 있고, 열림·잠김은 StageCatalog의 requires로 계산한다.
 * S2(판 시작·결과)가 requireStartable·recordResult를 부른다. 이름·인자를 바꿀 때는 S2 담당에게 먼저 알린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageProgressService {

    private static final Logger log = LoggerFactory.getLogger(StageProgressService.class);

    private final StageCatalog stageCatalog;
    private final AccountStageProgressRepository progressRepository;

    /** ST2: 스테이지마다 계정의 상태. 선택 화면을 열 때마다 불린다. 순서는 ST1과 같다 */
    public StageProgressListResponse getMyProgress(Long accountId) {
        Map<String, Instant> cleared = new HashMap<>();
        for (AccountStageProgress row : progressRepository.findAllByAccountId(accountId)) {
            if (stageCatalog.find(row.getStageId()).isEmpty()) {
                // 마스터 데이터에서 빠진 스테이지의 기록. 키는 지우지 않는 규칙이라 생기면 데이터 실수다
                log.warn("마스터 데이터에 없는 스테이지 진행 행을 건너뜀. accountId={}, stageId={}",
                        accountId, row.getStageId());
                continue;
            }
            cleared.put(row.getStageId(), row.getFirstClearedAt());
        }

        List<StageProgressResponse> stages = stageCatalog.stages().stream()
                .map(stage -> new StageProgressResponse(stage.stageId(),
                        StageStatusRule.of(stage, cleared.keySet()), cleared.get(stage.stageId())))
                .toList();
        return new StageProgressListResponse(cleared.size(), stageCatalog.totalCount(), stages);
    }

    /** 열렸는가(클리어한 스테이지 포함). 없는 키면 STAGE_NOT_FOUND */
    public boolean isUnlocked(Long accountId, String stageId) {
        StageDef stage = stageCatalog.get(stageId);
        return stage.requires() == null
                || progressRepository.existsByAccountIdAndStageId(accountId, stage.requires());
    }

    /**
     * 스테이지 플레이 시작 검사 한 줄(StagePlayService). 플레이 ID를 발급하기 전에 부른다. 통과하면 정의를 돌려준다.
     * 플레이 행에는 그 stageId와 waveCount를 저장한다(결과 검사는 플레이에 저장한 waveCount로, StageWaveRule).
     * 없는 키 → STAGE_NOT_FOUND, 잠김 → STAGE_LOCKED(HTTP 상태는 StageErrors).
     */
    public StageDef requireStartable(Long accountId, String stageId) {
        StageDef stage = stageCatalog.get(stageId);
        if (!isUnlocked(accountId, stage.stageId())) {
            throw StageErrors.locked();
        }
        return stage;
    }

    /**
     * S2 결과 트랜잭션 안에서, 결과 검사를 통과한 뒤 보상과 같은 곳에서 부른다(앞뒤 어디서 엔티티를 고쳐도 저장된다).
     * 트랜잭션 없이 부르면 예외(MANDATORY): 결과와 진행이 따로 커밋되어 어긋나는 일을 막는다.
     * stageId·stagePlayId는 요청 본문이 아니라 플레이 행에 저장된 값을 넘긴다. 같은 플레이 재전송으로 첫 응답을 돌려주는 길에서는 부르지 않는다.
     * 실패한 플레이(cleared false)는 아무것도 하지 않는다. 이미 클리어했으면 행을 그대로 둔다.
     * 같은 계정의 처음 클리어 둘이 동시에 오면 행은 하나지만 둘 다 true를 돌려줄 수 있다(반환값은 표시·로그용).
     *
     * @param stagePlayId 스테이지 플레이 ID(UUID 소문자·하이픈 36자, 플레이 행 값 그대로)
     * @return 이번에 처음 클리어했으면 true
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordResult(Long accountId, String stageId, boolean cleared, String stagePlayId) {
        if (!cleared) {
            return false;
        }
        StageDef stage = stageCatalog.get(stageId);
        if (progressRepository.existsByAccountIdAndStageId(accountId, stage.stageId())) {
            return false;
        }
        progressRepository.insertIfAbsent(accountId, stage.stageId(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS), stagePlayId);
        return true;
    }

}
