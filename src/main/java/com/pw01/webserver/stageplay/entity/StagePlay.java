package com.pw01.webserver.stageplay.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import com.pw01.webserver.stage.service.StageDef;
import com.pw01.webserver.stageplay.service.StagePlayErrors;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 스테이지 플레이 하나: 계정이 스테이지 하나를 한 번 플레이하는 것(시작 → 결과·포기·만료). 서버가 ID를 발급한다.
 * ID는 UUID 소문자·하이픈 36자(CHAR(36), utf8mb4_0900_bin). 스테이지와 웨이브 수는 요청이 아니라 시작 때의 서버 정의(StageDef)에서 넣고,
 * 결과 검사는 이 값으로 한다. 계정당 진행 중은 하나다(DB 유일 제약 uk_stage_play_one_in_progress, V5).
 * 상태는 한 방향(IN_PROGRESS → CLEARED·FAILED·EXPIRED)이고, 바꾸는 함수는 진행 중이 아니면 거절한다.
 */
@Entity
@Table(name = "stage_play")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StagePlay extends BaseEntity {

    /** 난이도 기본값: 난이도 조절 없음. 레벨 스케일링 등은 나중에 값을 더한다 */
    public static final int NO_DIFFICULTY = 0;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)   // CHAR(36)과 기동 검사(validate)를 맞춘다. 없으면 varchar로 보고 기동 실패
    @Column(name = "stage_play_id", length = 36)
    private String id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 64)
    private String stageId;

    /** 시작 때의 서버 waveCount */
    @Column(nullable = false)
    private int waveCount;

    @Column(nullable = false)
    private int difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StagePlayStatus status;

    /** 진행 중이면 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StagePlayEndReason endReason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String startRequestId;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant endedAt;

    public static StagePlay start(Long accountId, StageDef stage, int difficulty, String startRequestId, Instant now,
                                  Duration validity) {
        StagePlay play = new StagePlay();
        play.id = UUID.randomUUID().toString();
        play.accountId = accountId;
        play.stageId = stage.stageId();
        play.waveCount = stage.waveCount();
        play.difficulty = difficulty;
        play.status = StagePlayStatus.IN_PROGRESS;
        play.startRequestId = startRequestId;
        play.startedAt = now;
        play.expiresAt = now.plus(validity);
        return play;
    }

    public boolean isInProgress() {
        return status == StagePlayStatus.IN_PROGRESS;
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isAbandoned() {
        return endReason == StagePlayEndReason.ABANDONED;
    }

    /** 진행 중인데 마감이 지났으면 EXPIRED로 끝내고 true. 따로 도는 정리 작업 없이, 읽거나 새로 시작할 때 부른다 */
    public boolean expireIfDue(Instant now) {
        if (!isInProgress() || !isExpiredAt(now)) {
            return false;
        }
        end(StagePlayStatus.EXPIRED, StagePlayEndReason.EXPIRED, now);
        return true;
    }

    /** 포기: 실패로 끝내되 결과·보상은 없다(endReason ABANDONED). 진행 중이 아니면 409 */
    public void abandon(Instant now) {
        requireInProgress();
        end(StagePlayStatus.FAILED, StagePlayEndReason.ABANDONED, now);
    }

    /**
     * 결과가 저장될 때 결과 API가 같은 트랜잭션에서 부른다: 클리어면 CLEARED, 아니면 FAILED(endReason RESULT).
     * 진행 중이 아니면(포기·만료로 끝남) 409 STAGE_PLAY_NOT_IN_PROGRESS — 예외가 결과 트랜잭션 전체를 되돌린다.
     */
    public void endWithResult(boolean cleared, Instant now) {
        requireInProgress();
        end(cleared ? StagePlayStatus.CLEARED : StagePlayStatus.FAILED, StagePlayEndReason.RESULT, now);
    }

    private void requireInProgress() {
        if (!isInProgress()) {
            throw StagePlayErrors.notInProgress();
        }
    }

    private void end(StagePlayStatus endStatus, StagePlayEndReason reason, Instant now) {
        this.status = endStatus;
        this.endReason = reason;
        this.endedAt = now;
    }

}
