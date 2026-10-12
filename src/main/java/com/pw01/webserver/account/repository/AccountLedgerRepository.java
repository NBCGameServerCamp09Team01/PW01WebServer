package com.pw01.webserver.account.repository;

import com.pw01.webserver.account.entity.AccountLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerRepository extends JpaRepository<AccountLedger, Long> {

    /** 이 요청(requestId)으로 남긴 내역이 있나. 스탯 투자처럼 요청 하나가 여러 줄을 남기는 기능의 "이미 반영" 판정 */
    boolean existsByAccountIdAndRequestId(Long accountId, String requestId);

}
