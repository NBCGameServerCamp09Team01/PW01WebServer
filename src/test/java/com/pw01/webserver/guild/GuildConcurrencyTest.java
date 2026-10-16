package com.pw01.webserver.guild;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.common.error.ApiException;
import com.pw01.webserver.guild.dto.GuildCreateRequest;
import com.pw01.webserver.guild.dto.GuildJoinRequestCreateRequest;
import com.pw01.webserver.guild.service.GuildCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 길드 동시성 테스트 C-4~C-7(학습 문서 5-7, 설계 5장). 여러 스레드를 CountDownLatch로 같이 출발시켜 같은 행을 두고 부딪히게 한다.
 * 결과는 "성공 수와 실패 코드"로 본다. 서버가 다시 하기(ProgressWriter)를 다 써도 부딪히면 VERSION_CONFLICT(retryable)가
 * 나올 수 있으므로 실패 코드는 기대 코드 또는 VERSION_CONFLICT를 허용한다. 지켜야 하는 것은 DB의 최종 상태(불변 조건)다.
 * 실패하면 GuildMembershipTx의 잠금 순서·saveAndFlush 위치, AccountGuildReader.getOrCreateForApply의 첫 SQL을 먼저 의심한다.
 */
@IntegrationTest
class GuildConcurrencyTest {

    private static final String OK = "OK";
    private static final String VERSION_CONFLICT = "VERSION_CONFLICT";

    @Autowired
    GuildCommandService commands;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetCount() {
        GuildTestAccounts.resetGuildCount(jdbc);
    }

    // C-4: 서로 다른 다섯 명이 같은 이름으로 동시에 만들기 → 하나만 성공, 그 이름의 guild 행은 하나(I-4)
    @Test
    void 같은_이름_동시_만들기() throws Exception {
        String name = "동시" + GuildTestAccounts.suffix();
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long accountId = GuildTestAccounts.clearedAccount(jdbc);
            tasks.add(() -> call(() -> commands.create(accountId, new GuildCreateRequest(uuid(), name))));
        }

        List<String> results = runTogether(tasks);

        assertThat(results).filteredOn(OK::equals).hasSize(1);
        assertThat(results).filteredOn(r -> !OK.equals(r)).allMatch(r -> r.equals("GUILD_NAME_DUPLICATED")
                || r.equals(VERSION_CONFLICT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild WHERE name = ?", Integer.class, name)).isEqualTo(1);
    }

    // C-5: 상한 5에서 열 명이 다른 이름으로 동시에 만들기 → 성공 ≤ 5, guild_counter.guild_count = 성공 수(I-5)
    @Test
    void 상한에서_동시_만들기() throws Exception {
        String prefix = "상" + GuildTestAccounts.suffix();
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long accountId = GuildTestAccounts.clearedAccount(jdbc);
            String name = prefix + i;
            tasks.add(() -> call(() -> commands.create(accountId, new GuildCreateRequest(uuid(), name))));
        }

        List<String> results = runTogether(tasks);

        long created = results.stream().filter(OK::equals).count();
        assertThat(created).isBetween(1L, 5L);
        assertThat(results).filteredOn(r -> !OK.equals(r)).allMatch(r -> r.equals("GUILD_LIMIT_REACHED")
                || r.equals(VERSION_CONFLICT));
        assertThat(jdbc.queryForObject("SELECT guild_count FROM guild_counter", Integer.class)).isEqualTo((int) created);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild WHERE name LIKE ?", Integer.class, prefix + "%"))
                .isEqualTo((int) created);
    }

    // C-6: 한 사람이 네 길드에 동시에 신청 → account_guild 행 잠금으로 차례로 서서 정확히 3건 성공, 1건 LIMIT(I-6)
    @Test
    void 한_사람_동시_신청() throws Exception {
        long applicant = GuildTestAccounts.account(jdbc);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long guildId = GuildTestAccounts.guildWithLeader(jdbc, "신" + i + GuildTestAccounts.suffix(),
                    GuildTestAccounts.account(jdbc), 1, 30);
            tasks.add(() -> call(() -> commands.apply(applicant,
                    new GuildJoinRequestCreateRequest(uuid(), String.valueOf(guildId)))));
        }

        List<String> results = runTogether(tasks);

        assertThat(results).filteredOn(OK::equals).hasSize(3);
        assertThat(results).filteredOn(r -> !OK.equals(r)).containsExactly("GUILD_JOIN_REQUEST_LIMIT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild_join_request WHERE account_id = ?",
                Integer.class, applicant)).isEqualTo(3);
    }

    // C-7: 같은 requestId를 동시에 두 번 → 길드는 하나. 다른 쪽은 같은 응답 또는 REQUEST_IN_PROGRESS(I-10)
    @Test
    void 같은_요청_동시_두_번() throws Exception {
        long accountId = GuildTestAccounts.clearedAccount(jdbc);
        String name = "멱등" + GuildTestAccounts.suffix();
        GuildCreateRequest request = new GuildCreateRequest(uuid(), name);
        List<Callable<String>> tasks = List.of(
                () -> call(() -> commands.create(accountId, request)),
                () -> call(() -> commands.create(accountId, request)));

        List<String> results = runTogether(tasks);

        assertThat(results).contains(OK);
        assertThat(results).allMatch(r -> r.equals(OK) || r.equals("REQUEST_IN_PROGRESS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guild WHERE name = ?", Integer.class, name)).isEqualTo(1);
    }

    /** 작업들을 같이 출발시켜 결과(OK 또는 오류 코드)를 순서대로 모은다 */
    private static List<String> runTogether(List<Callable<String>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
            for (Callable<String> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private static String call(Runnable action) {
        try {
            action.run();
            return OK;
        } catch (ApiException e) {
            return e.getCode();
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

}
