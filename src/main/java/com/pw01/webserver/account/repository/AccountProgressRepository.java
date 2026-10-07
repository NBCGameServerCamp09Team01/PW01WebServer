package com.pw01.webserver.account.repository;

import com.pw01.webserver.account.entity.AccountProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountProgressRepository extends JpaRepository<AccountProgress, Long> {
}
