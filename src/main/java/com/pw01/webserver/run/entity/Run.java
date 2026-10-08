package com.pw01.webserver.run.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import com.pw01.webserver.stage.service.StageDef;
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
 * 판 하나(서버 발급). 판 번호는 UUID 소문자·하이픈 36자(CHAR(36), utf8mb4_0900_bin).
 * 판 번호·스테이지·웨이브 수 칸과 issue의 모양은 d(스테이지 담당)가 쓴 줄이다(stage-lines-for-c-1008.md 2장).
 * 스테이지와 웨이브 수는 요청이 아니라 판 시작 때의 서버 정의(StageDef)에서 넣는다. 결과 검사는 이 값으로 한다.
 */
@Entity
@Table(name = "run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Run extends BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)   // CHAR(36)과 기동 검사(validate)를 맞춘다. 없으면 varchar로 보고 기동 실패
    @Column(name = "run_id", length = 36)
    private String id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 64)
    private String stageId;

    /** 판 시작 때의 서버 waveCount */
    @Column(nullable = false)
    private int waveCount;

    @Column(nullable = false)
    private int difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String startRequestId;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant finishedAt;

    public static Run issue(Long accountId, StageDef stage, int difficulty, String startRequestId, Instant now,
                            Duration validity) {
        Run run = new Run();
        run.id = UUID.randomUUID().toString();
        run.accountId = accountId;
        run.stageId = stage.stageId();
        run.waveCount = stage.waveCount();
        run.difficulty = difficulty;
        run.status = RunStatus.ISSUED;
        run.startRequestId = startRequestId;
        run.issuedAt = now;
        run.expiresAt = now.plus(validity);
        return run;
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void finish(Instant now) {
        this.status = RunStatus.FINISHED;
        this.finishedAt = now;
    }

}
