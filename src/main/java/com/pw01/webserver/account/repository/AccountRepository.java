package com.pw01.webserver.account.repository;


import com.pw01.webserver.account.entity.Account;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    /** 로그인 조회. login_id 칼럼이 utf8mb4_0900_bin이라 대소문자까지 같아야 찾는다(WARRIOR01로 Warrior01을 찾지 않음, V2) */
    Optional<Account> findByLoginId(String loginId);

    /** 계정 ID를 작은 것부터 나눠 읽는다(누적 통계 대조). afterId보다 큰 것부터 page 크기만큼 */
    @Query("select a.id from Account a where a.id > :afterId order by a.id")
    List<Long> findIdsAfter(Long afterId, Pageable page);
}
