package com.pw01.webserver.run.entity;

import com.pw01.webserver.account.service.LevelGain;
import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 판의 결과 요약 + 서버가 계산한 보상. 기본 키가 판 번호라서 같은 판의 결과는 한 줄뿐이다(두 번 반영 막는 마지막 방어).
 * 같은 판의 결과가 다시 오면 이 줄을 그대로 돌려준다(result-api.md "재전송과 한 번만 반영").
 * 저장 시각(createdAt)이 응답의 submittedAt이다.
 */
@Entity
@Table(name = "run_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunResult extends BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 64)
    private String stageId;

    @Column(nullable = false)
    private int difficulty;

    @Column(nullable = false)
    private boolean cleared;

    @Column(nullable = false)
    private int reachedWave;

    @Column(nullable = false)
    private int waveCount;

    /** 게임이 보낸 전체 웨이브 수(검사에 쓰지 않음, 어긋남 추적용) */
    @Column(nullable = false)
    private int reportedWaveCount;

    @Column(nullable = false)
    private long playTimeMs;

    @Column(nullable = false)
    private int earnedGold;

    @Column(nullable = false)
    private int killCount;

    @Column(nullable = false)
    private int expGained;

    @Column(nullable = false)
    private int levelBefore;

    @Column(nullable = false)
    private int levelAfter;

    @Column(nullable = false)
    private int statPointsGained;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String requestId;

    /** 검사를 통과한 결과와 계산한 보상으로 한 줄을 만든다. 판의 값(스테이지·난이도·웨이브 수)은 판 행에서 가져온다 */
    public static RunResult of(Run run, String requestId, boolean cleared, int reachedWave, int reportedWaveCount,
                               long playTimeMs, int earnedGold, int killCount, LevelGain gain) {
        RunResult result = new RunResult();
        result.runId = run.getId();
        result.accountId = run.getAccountId();
        result.stageId = run.getStageId();
        result.difficulty = run.getDifficulty();
        result.waveCount = run.getWaveCount();
        result.requestId = requestId;
        result.cleared = cleared;
        result.reachedWave = reachedWave;
        result.reportedWaveCount = reportedWaveCount;
        result.playTimeMs = playTimeMs;
        result.earnedGold = earnedGold;
        result.killCount = killCount;
        result.expGained = gain.expGained();
        result.levelBefore = gain.levelBefore();
        result.levelAfter = gain.levelAfter();
        result.statPointsGained = gain.statPointsGained();
        return result;
    }

}
