package com.pw01.webserver.stage.repository;

import com.pw01.webserver.stage.entity.AccountStageProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AccountStageProgressRepository extends JpaRepository<AccountStageProgress, Long> {

    List<AccountStageProgress> findAllByAccountId(Long accountId);

    boolean existsByAccountIdAndStageId(Long accountId, String stageId);

    /**
     * 클리어 행을 넣는다. 같은 (계정, 스테이지) 행이 이미 있으면 아무것도 바꾸지 않는다(예외 없음).
     * 예외가 나면 부른 쪽(S2 결과 트랜잭션) 전체가 되돌아가므로, 동시에 같은 클리어가 와도 예외가 나지 않게 한다.
     * INSERT IGNORE는 외래 키·CHECK 위반까지 조용히 넘기므로 쓰지 않는다.
     * 네이티브 쿼리라 생성·수정 시각을 자동으로 채우지 않아 clearedAt으로 함께 넣는다.
     * 반환 행 수는 드라이버 설정에 따라 "이미 있음"에서도 0이 아닐 수 있으므로 처음 클리어 판단에 쓰지 않는다.
     * S2 결과 트랜잭션 안에서 불리므로 영속성 컨텍스트를 비우지 않는다(clearAutomatically 금지).
     * 비우면 S2가 읽어 둔 엔티티(계정 진행·판·결과)가 관리 밖이 되어, 이 쿼리 뒤의 변경이 조용히 저장되지 않는다.
     * 쿼리 전에 미반영 변경은 DB로 내보낸다(flushAutomatically).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO account_stage_progress
                (account_id, stage_id, first_cleared_at, first_clear_run_id, created_at, updated_at)
            VALUES (:accountId, :stageId, :clearedAt, :runId, :clearedAt, :clearedAt)
            ON DUPLICATE KEY UPDATE account_id = account_id""", nativeQuery = true)
    int insertIfAbsent(@Param("accountId") Long accountId, @Param("stageId") String stageId,
                       @Param("clearedAt") Instant clearedAt, @Param("runId") String runId);

}
