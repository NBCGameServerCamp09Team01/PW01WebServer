package com.pw01.webserver.account.repository;

import com.pw01.webserver.account.entity.AccountLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerRepository extends JpaRepository<AccountLedger, Long> {
}
