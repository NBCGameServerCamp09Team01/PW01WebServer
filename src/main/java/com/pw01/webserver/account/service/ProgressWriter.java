package com.pw01.webserver.account.service;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.config.ProgressProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 계정 진행 행(account_progress)을 바꾸는 저장의 한 곳(지시서 SF-6). 결과 제출(S2)·투자(S3)가 쓰고 해금(S4)이 이어 쓴다.
 * 진행 행을 바꾸는 저장은 자기 재시도 루프를 두지 않고 이 클래스를 거친다.
 * work를 새 트랜잭션에서 실행하고, 같은 계정의 다른 저장과 부딪히면(진행 행 @Version, 또는 같은 행을 동시에 처음 넣어
 * 유일 제약 위반) 트랜잭션을 **새로 열어** 처음부터 다시 한다(같은 트랜잭션에서 다시 하면 계속 실패한다).
 * 비관적 잠금 실패(MySQL 교착 1213·잠금 대기 초과 → PessimisticLockingFailureException)도 같이 다시 한다(M23 길드, G-43).
 * 교착은 MySQL이 한쪽 트랜잭션을 이미 되돌린 뒤라, 새 트랜잭션으로 다시 하면 대개 통과한다.
 * 정한 횟수(pw01.progress.save-attempts)를 넘으면 409 VERSION_CONFLICT(retryable true).
 * work는 다시 실행돼도 같은 결과가 나와야 한다: 시작할 때 "이미 반영됐나"를 먼저 보고, 판정에 걸리면 아무것도 저장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ProgressWriter {

    private static final Logger log = LoggerFactory.getLogger(ProgressWriter.class);

    private final TransactionTemplate transactionTemplate;
    private final ProgressProperties progressProperties;

    /** 유일 제약 위반은 모두 "동시에 같은 행을 넣음"으로 보고 다시 한다 */
    public <T> T run(String label, Supplier<T> work) {
        return run(label, work, e -> true);
    }

    /**
     * retryOn: 유일 제약 위반 가운데 다시 할 것만 true. false면 다시 해도 같으므로 그대로 던진다(500).
     * 예: 결과 제출은 "같은 플레이 결과가 먼저 저장됨"일 때만 다시 한다(다시 하면 처음 결과를 받는다)
     */
    public <T> T run(String label, Supplier<T> work, Predicate<DataIntegrityViolationException> retryOn) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (OptimisticLockingFailureException | PessimisticLockingFailureException
                     | DataIntegrityViolationException e) {
                if (e instanceof DataIntegrityViolationException violation && !retryOn.test(violation)) {
                    throw violation;
                }
                if (attempt >= progressProperties.saveAttempts()) {
                    log.warn("진행 저장 충돌이 계속됨 {} attempts={}", label, attempt, e);
                    throw versionConflict();
                }
                log.info("진행 저장 충돌, 다시 함 {} attempt={} cause={}", label, attempt, e.getClass().getSimpleName());
            }
        }
    }

    /** 409: 같은 계정의 저장이 계속 부딪힘. 다시 보내면 될 수 있으므로 retryable true */
    static ConflictException versionConflict() {
        return new ConflictException(CommonErrorCode.VERSION_CONFLICT,
                "요청이 몰려 저장하지 못했습니다. 잠시 뒤 다시 보내 주세요.", true);
    }

}
