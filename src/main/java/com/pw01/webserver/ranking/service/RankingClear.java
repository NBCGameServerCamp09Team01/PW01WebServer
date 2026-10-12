package com.pw01.webserver.ranking.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 랭킹에 넘기는 결과 하나(RankingRecordService.recordClear의 입력). 랭킹은 결과 저장 쪽 엔티티를 받지 않고 이 값만 받는다.
 * 값은 모두 서버가 저장한 결과에서 온다(요청 본문이 아님).
 *
 * @param accountId   계정 ID
 * @param stagePlayId 스테이지 플레이 ID(결과 하나에 한 번만 기록됨)
 * @param stageId     플레이 행의 스테이지 키
 * @param difficulty  플레이 행의 난이도
 * @param cleared     클리어했나(아니면 아무것도 하지 않음)
 * @param playTimeMs  서버가 검사·저장한 클리어 시간(ms)
 * @param achievedAt  결과 저장 시각(stage_play_result.created_at, 밀리초까지) = 달성 시각
 */
public record RankingClear(long accountId, String stagePlayId, String stageId, int difficulty, boolean cleared,
                           long playTimeMs, Instant achievedAt) {

    public RankingClear {
        Objects.requireNonNull(stagePlayId);
        Objects.requireNonNull(stageId);
        Objects.requireNonNull(achievedAt);
    }

}
