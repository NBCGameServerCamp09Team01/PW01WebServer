package com.pw01.webserver.guild.service;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.account.entity.Account;
import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

/**
 * 사람 줄(account_guild)이 생기는 길 셋: 가입 이벤트(AccountGuildInitializer), 줄이 없을 때 그 자리에서(AccountGuildReader),
 * 그리고 그 둘이 겹쳐도 줄 하나.
 * 실패하면 AccountService.register의 이벤트 발행 위치, AccountGuildInitializer의 리스너 종류(@EventListener = 같은 트랜잭션),
 * AccountGuildRepository.insertIfAbsent 쿼리를 먼저 의심한다.
 * 테스트끼리 DB를 함께 쓰므로 계정 아이디·닉네임을 서로 다르게 쓴다.
 */
@IntegrationTest
class AccountGuildInitTest {

    @Autowired
    AccountService accountService;

    @Autowired
    AccountGuildReader reader;

    @Autowired
    AccountGuildRepository people;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    // 확인: 가입하면 같은 트랜잭션에서 무소속 줄이 생긴다(version 0)
    @Test
    void 가입하면_무소속_줄() {
        Account account = accountService.register("GuildIn01", "hash", "길드가입01", null);

        AccountGuild person = people.findById(account.getId()).orElseThrow();
        assertThat(person.isInGuild()).isFalse();
        assertThat(person.getVersion()).isZero();
    }

    // 확인: 중복 가입으로 가입이 실패하면 사람 줄도 없다(이벤트는 저장이 성공한 뒤에만 냄)
    @Test
    void 가입이_실패하면_줄도_없음() {
        accountService.register("GuildIn02", "hash", "길드가입02", null);
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM account_guild", Integer.class);

        assertThatThrownBy(() -> accountService.register("GuildIn02", "hash", "길드가입03", null));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_guild", Integer.class)).isEqualTo(before);
    }

    // 확인: 줄이 없는 계정(SQL로 만든 계정 = 옛 서버가 받은 가입)은 쓰기 트랜잭션에서 그 자리에서 만들어진다. 읽기는 빈 값
    @Test
    void 줄이_없으면_그_자리에서() {
        long accountId = insertAccount("GuildIn04", "길드가입04");
        assertThat(reader.find(accountId)).isEmpty();

        AccountGuild person = tx.execute(s -> reader.getOrCreate(accountId));

        assertThat(person.getAccountId()).isEqualTo(accountId);
        assertThat(person.isInGuild()).isFalse();
        assertThat(reader.find(accountId)).isPresent();
    }

    // 확인: 신청용은 줄이 없으면 만든 뒤 잡고, 잡을 때 version이 오른다(만든 줄 0 → 1)
    @Test
    void 신청용도_줄이_없으면_만들고_잡음() {
        long accountId = insertAccount("GuildIn05", "길드가입05");

        tx.executeWithoutResult(s -> reader.getOrCreateForApply(accountId));

        assertThat(people.findById(accountId).orElseThrow().getVersion()).isEqualTo(1L);
    }

    // 확인: 줄이 없는 계정에 둘이 동시에 getOrCreate해도 둘 다 성공, 줄은 하나
    @Test
    void 동시에_만들어도_줄_하나() throws Exception {
        long accountId = insertAccount("GuildIn06", "길드가입06");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> task = () -> {
            start.await();
            return tx.execute(s -> reader.getOrCreate(accountId).getAccountId());
        };

        List<Future<Long>> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results = List.of(pool.submit(task), pool.submit(task));
            start.countDown();
            for (Future<Long> result : results) {
                assertThat(result.get()).isEqualTo(accountId);
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_guild WHERE account_id = ?", Integer.class, accountId))
                .isEqualTo(1);
    }

    // 확인: 줄이 없는 계정에 신청용 둘이 동시에 와도 둘 다 성공하고 차례로 version을 올린다(0 → 1 → 2), 교착 없음
    @Test
    void 신청용을_동시에_불러도_차례로() throws Exception {
        long accountId = insertAccount("GuildIn08", "길드가입08");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> task = () -> {
            start.await();
            return tx.execute(s -> reader.getOrCreateForApply(accountId).getAccountId());
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Long>> results = List.of(pool.submit(task), pool.submit(task));
            start.countDown();
            for (Future<Long> result : results) {
                assertThat(result.get()).isEqualTo(accountId);
            }
        }
        assertThat(people.findById(accountId).orElseThrow().getVersion()).isEqualTo(2L);
    }

    // 확인: 만드는 길은 쓰기 트랜잭션 안에서만(트랜잭션 없이 부르면 바로 실패 — 부르는 쪽 실수)
    @Test
    void 트랜잭션_밖에서는_실패() {
        long accountId = insertAccount("GuildIn07", "길드가입07");

        assertThatThrownBy(() -> reader.getOrCreate(accountId)).isInstanceOf(IllegalTransactionStateException.class);
    }

    private long insertAccount(String loginId, String nickname) {
        jdbc.update("""
                INSERT INTO account (login_id, password_hash, nickname, email, status, created_at, updated_at)
                VALUES (?, 'hash', ?, NULL, 'ACTIVE', NOW(3), NOW(3))""", loginId, nickname);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE login_id = ?", Long.class, loginId);
    }

}
