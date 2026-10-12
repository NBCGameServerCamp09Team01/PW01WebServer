package com.pw01.webserver.account.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정의 스탯 하나에 투자한 포인트(account_stat, S3). 기본 키 = 계정 + 스탯 태그.
 * 버전은 두지 않는다. 바꿀 때는 같은 트랜잭션에서 진행 행(account_progress)의 version을 올려 동시 저장을 막는다.
 */
@Entity
@Table(name = "account_stat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStat extends BaseEntity {

    @EmbeddedId
    private AccountStatId id;

    @Column(nullable = false)
    private int points;

    public static AccountStat empty(Long accountId, String statKey) {
        AccountStat stat = new AccountStat();
        stat.id = new AccountStatId(accountId, statKey);
        stat.points = 0;
        return stat;
    }

    public String getStatKey() {
        return id.getStatKey();
    }

    /** points만큼 더한다(음수면 내림). 범위 판정은 서비스가 먼저 하고, 0 아래는 DB CHECK가 마지막으로 막는다 */
    public void add(int points) {
        this.points += points;
    }

}
