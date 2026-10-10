package com.pw01.webserver.account.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 계정 변경 내역 한 줄(지시서 SF-6, 1-2a). 진행 행(account_progress)이 "지금 값"이면 이 표는 "무엇이 왜 바뀌었나"다.
 * 넣기만 하고 고치지 않는다. 합계 대조: SUM(exp_delta) = 총 경험치, SUM(point_delta) = 스탯 포인트.
 * 같은 출처(계정·종류·출처 ID)는 한 줄(uk_account_ledger_source).
 */
@Entity
@Table(name = "account_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountLedger extends BaseEntity {

    /** 결과 보상 */
    public static final String KIND_STAGE_REWARD = "STAGE_REWARD";
    /** 출처: 스테이지 플레이 */
    public static final String SOURCE_STAGE_PLAY = "STAGE_PLAY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(length = 100)
    private String target;

    @Column(nullable = false)
    private long expDelta;

    @Column(nullable = false)
    private int pointDelta;

    @Column(nullable = false, length = 20)
    private String sourceType;

    @Column(nullable = false, length = 64)
    private String sourceId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String requestId;

    public static AccountLedger stageReward(Long accountId, String stagePlayId, String requestId, long expDelta,
                                            int pointDelta) {
        AccountLedger ledger = new AccountLedger();
        ledger.accountId = accountId;
        ledger.kind = KIND_STAGE_REWARD;
        ledger.expDelta = expDelta;
        ledger.pointDelta = pointDelta;
        ledger.sourceType = SOURCE_STAGE_PLAY;
        ledger.sourceId = stagePlayId;
        ledger.requestId = requestId;
        return ledger;
    }

}
