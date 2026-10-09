package com.pw01.webserver.stageresult.entity;

import com.pw01.webserver.account.service.LevelGain;
import com.pw01.webserver.common.entity.BaseEntity;
import com.pw01.webserver.stageplay.entity.StagePlay;
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
 * 스테이지 플레이의 결과 요약 + 서버가 계산한 보상. 기본 키가 스테이지 플레이 ID라서 같은 플레이의 결과는 한 줄뿐이다
 * (두 번 반영 막는 마지막 방어). 같은 플레이의 결과가 다시 오면 이 줄을 그대로 돌려준다.
 * 저장 시각(createdAt)이 응답의 submittedAt이다.
 */
@Entity
@Table(name = "stage_play_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StageResult extends BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "stage_play_id", length = 36)
    private String stagePlayId;

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

    /** 검사를 통과한 결과와 계산한 보상으로 한 줄을 만든다. 플레이의 값(스테이지·난이도·웨이브 수)은 플레이 행에서 가져온다 */
    public static StageResult of(StagePlay play, String requestId, boolean cleared, int reachedWave,
                                 int reportedWaveCount, long playTimeMs, int earnedGold, int killCount,
                                 LevelGain gain) {
        StageResult result = new StageResult();
        result.stagePlayId = play.getId();
        result.accountId = play.getAccountId();
        result.stageId = play.getStageId();
        result.difficulty = play.getDifficulty();
        result.waveCount = play.getWaveCount();
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
