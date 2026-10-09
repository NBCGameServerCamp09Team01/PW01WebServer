package com.pw01.webserver.stage.service;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.account.entity.AccountProgress;
import com.pw01.webserver.account.repository.AccountProgressRepository;
import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.common.error.ApiException;
import com.pw01.webserver.stage.dto.StageProgressListResponse;
import com.pw01.webserver.stage.dto.StageProgressResponse;
import com.pw01.webserver.stage.entity.AccountStageProgress;
import com.pw01.webserver.stage.repository.AccountStageProgressRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 스테이지 진행 서비스 통합 테스트: 내 진행(ST2) 상태 계산, 판 시작 판정(S2가 부름), 클리어 기록(S2 결과 트랜잭션이 부름).
 * recordResult는 결과 트랜잭션 안에서만 부르므로 시험도 TransactionTemplate으로 트랜잭션을 연다(S2 흉내).
 * 판 번호는 S2와 같은 UUID(소문자·하이픈 36자) 문자열이다.
 * 실패하면 StageStatusRule(상태), StageErrors(코드·HTTP 상태), recordResult의 분기와 insertIfAbsent를 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 계정 아이디·닉네임을 서로 다르게 쓴다.
 */
@IntegrationTest
class StageProgressServiceTest {

    private static final String RUN_1 = "3f2b8c1e-7a4d-4e2f-9b10-6c5d4e3f2a1b";
    private static final String RUN_2 = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    @Autowired
    StageProgressService service;

    @Autowired
    AccountService accountService;

    @Autowired
    AccountProgressRepository accountProgressRepository;

    @Autowired
    AccountStageProgressRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    EntityManager entityManager;

    // 확인: 새 계정 — 스테이지 1 OPEN, 2 LOCKED, 클리어 0, 처음 클리어 시각 없음, 순서는 ST1과 같음
    @Test
    void 새_계정_진행() {
        Long accountId = newAccount("StageSv01", "진행서비스01");

        StageProgressListResponse progress = service.getMyProgress(accountId);

        assertThat(progress.clearedCount()).isZero();
        assertThat(progress.totalCount()).isEqualTo(2);
        assertThat(progress.stages()).extracting(StageProgressResponse::stageId, StageProgressResponse::status)
                .containsExactly(
                        tuple("stage.01.01", StageStatus.OPEN),
                        tuple("stage.01.02", StageStatus.LOCKED));
        assertThat(progress.stages()).extracting(StageProgressResponse::firstClearedAt).containsOnlyNulls();
    }

    // 확인: 스테이지 1 클리어 → true, 1 CLEARED(시각 있음)·2 OPEN, 클리어 1
    @Test
    void 클리어하면_다음이_열림() {
        Long accountId = newAccount("StageSv02", "진행서비스02");

        assertThat(record(accountId, "stage.01.01", true, RUN_1)).isTrue();

        StageProgressListResponse progress = service.getMyProgress(accountId);
        assertThat(progress.clearedCount()).isEqualTo(1);
        assertThat(progress.stages().get(0).status()).isEqualTo(StageStatus.CLEARED);
        assertThat(progress.stages().get(0).firstClearedAt()).isNotNull();
        assertThat(progress.stages().get(1).status()).isEqualTo(StageStatus.OPEN);
    }

    // 확인: 실패한 판은 진행을 바꾸지 않는다
    @Test
    void 실패는_그대로() {
        Long accountId = newAccount("StageSv03", "진행서비스03");

        assertThat(record(accountId, "stage.01.01", false, RUN_1)).isFalse();

        assertThat(repository.findAllByAccountId(accountId)).isEmpty();
        assertThat(service.getMyProgress(accountId).stages().get(1).status()).isEqualTo(StageStatus.LOCKED);
    }

    // 확인: 다시 클리어해도 행 하나, 처음 클리어 시각·판 번호는 첫 값, 반환 false
    @Test
    void 다시_클리어해도_그대로() {
        Long accountId = newAccount("StageSv04", "진행서비스04");
        record(accountId, "stage.01.01", true, RUN_1);
        AccountStageProgress first = repository.findAllByAccountId(accountId).getFirst();

        assertThat(record(accountId, "stage.01.01", true, RUN_2)).isFalse();

        List<AccountStageProgress> rows = repository.findAllByAccountId(accountId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getFirstClearedAt()).isEqualTo(first.getFirstClearedAt());
        assertThat(rows.getFirst().getFirstClearStagePlayId()).isEqualTo(RUN_1);
    }

    // 확인: 트랜잭션 없이 부르면 예외(결과와 진행이 따로 커밋되는 것을 막음), 행도 없음
    @Test
    void 트랜잭션_밖에서는_예외() {
        Long accountId = newAccount("StageSv05", "진행서비스05");

        assertThatThrownBy(() -> service.recordResult(accountId, "stage.01.01", true, RUN_1))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(repository.findAllByAccountId(accountId)).isEmpty();
    }

    // 확인: recordResult 뒤에도 S2가 같은 트랜잭션에서 읽어 둔 엔티티가 관리 중이다.
    // 넣기 쿼리가 영속성 컨텍스트를 비우면(clearAutomatically) false가 되고, S2가 그 뒤에 고친 값이 조용히 저장되지 않는다
    @Test
    void 기록_뒤에도_S2의_엔티티가_관리_중() {
        Long accountId = newAccount("StageSv10", "진행서비스10");

        Boolean managed = tx.execute(status -> {
            AccountProgress progress = accountProgressRepository.findById(accountId).orElseThrow();
            service.recordResult(accountId, "stage.01.01", true, RUN_1);
            return entityManager.contains(progress);
        });

        assertThat(managed).isTrue();
    }

    // 확인: 판 시작 판정 — 잠김 STAGE_LOCKED 403, 없는 키 STAGE_NOT_FOUND 404, 열림이면 정의(웨이브 수 포함)
    @Test
    void 판_시작_판정() {
        Long accountId = newAccount("StageSv06", "진행서비스06");

        assertApiError(() -> service.requireStartable(accountId, "stage.01.02"),
                StageErrors.STAGE_LOCKED, HttpStatus.FORBIDDEN);
        assertApiError(() -> service.requireStartable(accountId, "stage.09.09"),
                StageErrors.STAGE_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertThat(service.requireStartable(accountId, "stage.01.01").waveCount()).isEqualTo(5);

        record(accountId, "stage.01.01", true, RUN_1);

        assertThat(service.requireStartable(accountId, "stage.01.02").stageId()).isEqualTo("stage.01.02");
        assertThat(service.isUnlocked(accountId, "stage.01.01")).isTrue();
    }

    // 확인: 한 계정의 클리어가 다른 계정의 진행에 섞이지 않는다
    @Test
    void 계정마다_따로() {
        Long first = newAccount("StageSv07", "진행서비스07");
        Long second = newAccount("StageSv08", "진행서비스08");

        record(first, "stage.01.01", true, RUN_1);

        assertThat(service.getMyProgress(second).stages().get(1).status()).isEqualTo(StageStatus.LOCKED);
        assertThat(service.isUnlocked(second, "stage.01.02")).isFalse();
    }

    // 확인: 같은 계정·스테이지의 처음 클리어 둘이 동시에 와도 예외 없이 행 하나(결과 트랜잭션이 되돌아가지 않음)
    @Test
    void 동시_클리어는_한_행() throws Exception {
        Long accountId = newAccount("StageSv09", "진행서비스09");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> clear = () -> {
            start.await();
            return record(accountId, "stage.01.01", true, RUN_1);
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = pool.submit(clear);
            Future<Boolean> b = pool.submit(clear);
            start.countDown();
            a.get();
            b.get();
        }

        assertThat(repository.findAllByAccountId(accountId)).hasSize(1);
    }

    private Long newAccount(String loginId, String nickname) {
        return accountService.register(loginId, "hash", nickname, null).getId();
    }

    private Boolean record(Long accountId, String stageId, boolean cleared, String runId) {
        return tx.execute(status -> service.recordResult(accountId, stageId, cleared, runId));
    }

    private static void assertApiError(ThrowingCallable call, String code, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getCode()).isEqualTo(code);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(status);
                });
    }

}
