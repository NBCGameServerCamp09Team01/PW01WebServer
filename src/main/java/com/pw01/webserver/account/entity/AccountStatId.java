package com.pw01.webserver.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** account_stat 기본 키(계정 + 스탯 태그) */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AccountStatId implements Serializable {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "stat_key", nullable = false, length = 100)
    private String statKey;

}
