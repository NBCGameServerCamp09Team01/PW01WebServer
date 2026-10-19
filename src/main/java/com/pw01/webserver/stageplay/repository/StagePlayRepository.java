package com.pw01.webserver.stageplay.repository;

import com.pw01.webserver.stageplay.entity.StagePlay;
import com.pw01.webserver.stageplay.entity.StagePlayStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StagePlayRepository extends JpaRepository<StagePlay, String> {

    /** 내 플레이만 찾는다. 남의 플레이는 없는 것과 같다(STAGE_PLAY_NOT_FOUND, 있는지 알려 주지 않음) */
    Optional<StagePlay> findByIdAndAccountId(String id, Long accountId);

    /** 같은 시작 요청(requestId)을 다시 보냈을 때 처음 플레이를 돌려주려고 찾는다 */
    Optional<StagePlay> findByAccountIdAndStartRequestId(Long accountId, String startRequestId);

    /** 진행 중 플레이는 계정당 하나(DB 유일 제약)라 하나만 나온다 */
    Optional<StagePlay> findByAccountIdAndStatus(Long accountId, StagePlayStatus status);

    /** 모든 계정의 진행 중 플레이(마감 전), 최근 시작 순 50개 */
    List<StagePlay> findTop50ByStatusAndExpiresAtAfterOrderByStartedAtDesc(StagePlayStatus status, Instant now);
}
