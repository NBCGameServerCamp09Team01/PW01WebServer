package com.pw01.webserver.account.repository;

import com.pw01.webserver.account.entity.AccountSkill;
import com.pw01.webserver.account.entity.AccountSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountSkillRepository extends JpaRepository<AccountSkill, AccountSkillId> {

    List<AccountSkill> findByIdAccountId(Long accountId);

}
