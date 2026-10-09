package com.pw01.webserver.stage.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 계정이 클리어한 스테이지 한 행(account_stage_progress). 행이 있으면 클리어한 것이다.
 * 읽기용이다. 넣기는 동시에 와도 예외가 나지 않도록 저장소의 insertIfAbsent(네이티브 쿼리)로 하고, 고치지 않는다.
 */
@Entity
@Table(name = "account_stage_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStageProgress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_stage_progress_id")
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 64)
    private String stageId;

    @Column(nullable = false)
    private Instant firstClearedAt;

    /**
     * 처음 클리어한 스테이지 플레이 ID(UUID 소문자·하이픈 36자). stage_play 표와 같은 정의, 외래 키 없음.
     * DB 칼럼 이름은 V4(dev 병합됨) 그대로 first_clear_run_id다(예전 이름 "판 번호", 값은 같은 것).
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "first_clear_run_id", length = 36)
    private String firstClearStagePlayId;

}
