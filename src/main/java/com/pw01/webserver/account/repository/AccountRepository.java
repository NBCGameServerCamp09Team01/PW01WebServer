package com.pw01.webserver.account.repository;


import com.pw01.webserver.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);
}
