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
    /** 스탯 올림(포인트를 씀) */
    public static final String KIND_STAT_INVEST = "STAT_INVEST";
    /** 스탯 내림(포인트를 돌려받음) */
    public static final String KIND_STAT_REFUND = "STAT_REFUND";
    /** 출처: 스테이지 플레이 */
    public static final String SOURCE_STAGE_PLAY = "STAGE_PLAY";
    /** 출처: 스탯 투자 요청. source_id = "requestId:스탯태그"(한 요청이 스탯마다 한 줄) */
    public static final String SOURCE_STAT_INVEST = "STAT_INVEST";

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

    /**
     * 스탯 투자 한 줄. points가 양수면 올림(포인트 −points), 음수면 내림(포인트 +|points|).
     * source_id는 "requestId:스탯태그"라 같은 요청의 같은 스탯은 한 줄뿐이다(uk_account_ledger_source = 두 번 반영 방지의 마지막 방어)
     */
    public static AccountLedger statChange(Long accountId, String requestId, String statKey, int points) {
        AccountLedger ledger = new AccountLedger();
        ledger.accountId = accountId;
        ledger.kind = points > 0 ? KIND_STAT_INVEST : KIND_STAT_REFUND;
        ledger.target = statKey;
        ledger.expDelta = 0;
        ledger.pointDelta = -points;
        ledger.sourceType = SOURCE_STAT_INVEST;
        ledger.sourceId = requestId + ":" + statKey;
        ledger.requestId = requestId;
        return ledger;
    }

}
