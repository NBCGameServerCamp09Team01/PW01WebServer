package com.pw01.webserver.account.entity;

import com.pw01.webserver.account.service.LevelGain;
import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountProgress extends BaseEntity{

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int experience;

    @Column(nullable = false)
    private long totalExperience;

    @Column(nullable = false)
    private int statPoints;

    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * 경험치 계산 결과(LevelCurve.gain)를 반영한다. 계산은 LevelCurve가 하고 여기서는 값만 옮긴다.
     * version은 저장(flush)할 때 JPA가 올린다(@Version, 낙관적 락).
     */
    public void apply(LevelGain gain) {
        this.level = gain.levelAfter();
        this.experience = gain.experienceAfter();
        this.totalExperience = gain.totalAfter();
        this.statPoints += gain.statPointsGained();
    }

    public static AccountProgress initial(Long accountId){
        AccountProgress progress = new AccountProgress();
        progress.accountId = accountId;
        progress.level = 1;
        progress.experience = 0;
        progress.totalExperience = 0;
        progress.statPoints = 0;
        return progress;
    }
}
