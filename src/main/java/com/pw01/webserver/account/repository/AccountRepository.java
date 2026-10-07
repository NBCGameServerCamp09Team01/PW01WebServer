package com.pw01.webserver.account.repository;


import com.pw01.webserver.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    /** 로그인 조회. login_id 칼럼이 utf8mb4_0900_bin이라 대소문자까지 같아야 찾는다(WARRIOR01로 Warrior01을 찾지 않음, V2) */
    Optional<Account> findByLoginId(String loginId);
}
