package com.pw01.webserver.ranking.service;

import com.pw01.webserver.ranking.repository.RankingRecordRepository;
import com.pw01.webserver.ranking.repository.RankingRecordRepository.RegionSum;
import com.pw01.webserver.ranking.service.RankingClearRecordedEvent.BoardUpdate;
import com.pw01.webserver.stage.service.RegionDef;
import com.pw01.webserver.stage.service.StageCatalog;
import com.pw01.webserver.stage.service.StageDef;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 클리어 결과를 랭킹 표(MySQL, 원천)에 기록한다. 결과를 저장하는 트랜잭션 안에서 한 번 불린다.
 * <ol>
 *   <li>클리어가 아니면 아무것도 하지 않는다</li>
 *   <li>점수 계산(RankingScore) → ranking_clear_log 한 줄(기록 순번)</li>
 *   <li>스테이지 최고 기록: 점수가 오를 때만 덮어씀</li>
 *   <li>올랐으면 지역 점수·전체 총합(스테이지 최고의 합)을 다시 계산해 덮어씀</li>
 *   <li>이 스테이지를 처음 클리어했으면 진행(클리어한 스테이지 수·가장 먼 스테이지)을 다시 계산해 덮어씀</li>
 *   <li>커밋 뒤 Redis에 옮길 값을 이벤트로 낸다(RankingCacheUpdater)</li>
 * </ol>
 * 연동: 결과 저장 StageResultService.submitOnce 6번이 결과 행을 저장한 뒤 같은 트랜잭션에서 부른다. 재전송(ALREADY_SAVED) 길에서는 부르지 않는다.
 * 트랜잭션 없이 부르면 예외(MANDATORY): 결과와 랭킹이 따로 커밋되어 어긋나는 일을 막는다.
 */
@Service
@RequiredArgsConstructor
public class RankingRecordService {

    private static final Logger log = LoggerFactory.getLogger(RankingRecordService.class);

    private final StageCatalog stageCatalog;
    private final RankingScore rankingScore;
    private final RankingRecordRepository recordRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordClear(RankingClear clear) {
        if (!clear.cleared()) {
            return;
        }
        StageDef stage = stageCatalog.get(clear.stageId());
        RegionDef region = stageCatalog.getRegion(stage.regionId());
        long accountId = clear.accountId();
        int difficulty = clear.difficulty();
        Instant at = clear.achievedAt().truncatedTo(ChronoUnit.MILLIS);
        int score = rankingScore.of(stage.recommendedClearSeconds(), clear.playTimeMs(), difficulty);

        // 처음 클리어 판단은 기록을 넣기 전에(넣은 뒤에는 늘 있음)
        boolean firstClear = !recordRepository.hasClear(accountId, stage.stageId());
        long clearSeq = recordRepository.insertClear(clear.stagePlayId(), accountId, stage.stageId(), region.regionId(),
                difficulty, score, clear.playTimeMs(), stage.recommendedClearSeconds() * 1000L, at);

        List<BoardUpdate> updates = new ArrayList<>();
        // 스테이지 보드: 이번 기록을 그대로 넘긴다. Redis는 더 좋을 때만 바꾸므로 전체·주간 모두 맞다
        RankingRow stageRow = new RankingRow(accountId, score, at, clearSeq, Long.toString(clear.playTimeMs()));
        updates.add(new BoardUpdate(RankingBoard.stage(stage.stageId(), difficulty, RankingPeriod.ALL, at), stageRow));
        updates.add(new BoardUpdate(RankingBoard.stage(stage.stageId(), difficulty, RankingPeriod.WEEKLY, at), stageRow));

        recordRepository.upsertStageBest(stage.stageId(), difficulty, accountId, score, clear.playTimeMs(), at, clearSeq,
                clear.stagePlayId());
        boolean stageImproved = recordRepository.stageBestClearSeq(stage.stageId(), difficulty, accountId) == clearSeq;
        if (stageImproved) {
            List<String> regionStageIds = region.stages().stream().map(StageDef::stageId).toList();
            RegionSum sum = recordRepository.sumRegion(accountId, difficulty, regionStageIds);
            recordRepository.upsertRegion(region.regionId(), difficulty, accountId, sum.total(),
                    sum.clearedStageCount(), at, clearSeq);
            updates.add(new BoardUpdate(RankingBoard.region(region.regionId(), difficulty, RankingPeriod.ALL, at),
                    new RankingRow(accountId, sum.total(), at, clearSeq, Integer.toString(sum.clearedStageCount()))));

            RegionSum all = recordRepository.sumAll(accountId, difficulty);
            recordRepository.upsertTotal(difficulty, accountId, all.total(), all.clearedStageCount(), at, clearSeq);
            updates.add(new BoardUpdate(RankingBoard.total(difficulty, RankingPeriod.ALL, at),
                    new RankingRow(accountId, all.total(), at, clearSeq, Integer.toString(all.clearedStageCount()))));
        }

        if (firstClear) {
            progressRow(accountId, at, clearSeq).ifPresent(row -> {
                recordRepository.upsertProgress(accountId, (int) row.score(), row.detail(), at, clearSeq);
                updates.add(new BoardUpdate(RankingBoard.progress(), row));
            });
        }

        log.info("랭킹 기록 stagePlayId={} stage={} d={} score={} clearSeq={} stageImproved={} firstClear={}",
                clear.stagePlayId(), stage.stageId(), difficulty, score, clearSeq, stageImproved, firstClear);
        eventPublisher.publishEvent(new RankingClearRecordedEvent(accountId, updates, List.of(
                RankingBoard.region(region.regionId(), difficulty, RankingPeriod.WEEKLY, at),
                RankingBoard.total(difficulty, RankingPeriod.WEEKLY, at))));
    }

    /** 진행 줄: 클리어 기록한 스테이지 중 지금 마스터 데이터에 있는 것의 수, 가장 뒤(지역 order → 스테이지 order) */
    private Optional<RankingRow> progressRow(long accountId, Instant at, long clearSeq) {
        Map<String, Integer> position = positions();
        List<String> cleared = recordRepository.clearedStageIds(accountId).stream()
                .filter(position::containsKey)
                .toList();
        return cleared.stream()
                .max(Comparator.comparingInt(position::get))
                .map(furthest -> new RankingRow(accountId, cleared.size(), at, clearSeq, furthest));
    }

    /** 스테이지 키 → 화면 순서(지역 order → 스테이지 order 순으로 0부터). StageCatalog.stages()가 그 순서다 */
    private Map<String, Integer> positions() {
        List<StageDef> stages = stageCatalog.stages();
        Map<String, Integer> position = new HashMap<>();
        for (int i = 0; i < stages.size(); i++) {
            position.put(stages.get(i).stageId(), i);
        }
        return position;
    }

}
