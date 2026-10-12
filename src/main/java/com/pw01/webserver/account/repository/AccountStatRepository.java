package com.pw01.webserver.account.repository;

import com.pw01.webserver.account.entity.AccountStat;
import com.pw01.webserver.account.entity.AccountStatId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountStatRepository extends JpaRepository<AccountStat, AccountStatId> {

    List<AccountStat> findByIdAccountId(Long accountId);

}
