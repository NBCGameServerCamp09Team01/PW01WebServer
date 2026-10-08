package com.pw01.webserver.run.repository;

import com.pw01.webserver.run.entity.Run;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RunRepository extends JpaRepository<Run, String> {

    /** 내 판만 찾는다. 남의 판은 없는 것과 같다(RUN_NOT_FOUND, 있는지 알려 주지 않음) */
    Optional<Run> findByIdAndAccountId(String id, Long accountId);

    /** 같은 판 시작 요청(requestId)을 다시 보냈을 때 처음 판을 돌려주려고 찾는다 */
    Optional<Run> findByAccountIdAndStartRequestId(Long accountId, String startRequestId);
}
