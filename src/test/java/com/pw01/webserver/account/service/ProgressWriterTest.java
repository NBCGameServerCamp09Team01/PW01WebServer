package com.pw01.webserver.account.service;

import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.config.ProgressProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ProgressWriter의 재시도 규칙(DB 없이): 어떤 실패를 새 트랜잭션으로 다시 하고, 언제 409 VERSION_CONFLICT로 끝내나.
 * 트랜잭션 템플릿은 콜백을 바로 실행하는 가짜다(재시도 횟수·예외 분기만 본다).
 * 실패하면 ProgressWriter.run의 catch 목록과 retryOn 분기를 먼저 의심한다.
 */
class ProgressWriterTest {

    private final ProgressWriter writer = new ProgressWriter(new DirectTransactionTemplate(), new ProgressProperties(3));

    // 확인: 교착(MySQL 1213 → CannotAcquireLockException, PessimisticLockingFailureException의 하위)도 다시 한다(G-43)
    @Test
    void 교착은_다시_함() {
        AtomicInteger calls = new AtomicInteger();

        String result = writer.run("test", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new CannotAcquireLockException("Deadlock found when trying to get lock");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    // 확인: 낙관적 잠금 실패가 정한 횟수(3)를 넘으면 409 VERSION_CONFLICT(retryable true)
    @Test
    void 횟수를_넘으면_409() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> writer.run("test", () -> {
            calls.incrementAndGet();
            throw new OptimisticLockingFailureException("version");
        }))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "VERSION_CONFLICT")
                .hasFieldOrPropertyWithValue("retryable", true);
        assertThat(calls).hasValue(3);
    }

    // 확인: retryOn이 false인 유일 제약 위반은 다시 하지 않고 그대로 던진다(부르는 쪽이 409로 바꿈)
    @Test
    void 다시_하지_않을_위반은_그대로() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> writer.run("test", () -> {
            calls.incrementAndGet();
            throw new DataIntegrityViolationException("uk_guild_name");
        }, e -> false)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(calls).hasValue(1);
    }

    /** 콜백을 트랜잭션 없이 바로 실행하는 가짜 */
    private static class DirectTransactionTemplate extends TransactionTemplate {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }

    }

}
