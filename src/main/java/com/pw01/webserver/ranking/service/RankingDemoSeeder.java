package com.pw01.webserver.ranking.service;

import com.pw01.webserver.account.service.AccountService;
import com.pw01.webserver.stage.service.StageCatalog;
import com.pw01.webserver.stage.service.StageDef;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 로컬 시연용 랭킹 데이터(계정이 적으면 랭킹이 비어 보이는 문제). **local 프로필 + 실행 인자가 있을 때만** 돈다. 운영에는 없다.
 * <pre>
 * 실행 인자 예: --pw01.ranking.seed.accounts=200 --pw01.ranking.seed.clears-per-account=4
 * </pre>
 * 계정은 계정 기능(AccountService.register)으로 만들고(로그인할 수 없는 시드 계정, 아이디 seedNNNN),
 * 클리어는 실제 길(RankingRecordService.recordClear)로 지난 20일 안에 흩어 기록한다. Redis 보드는 다음 조회 때 다시 채워진다.
 * 같은 인자로 두 번 돌리면 아이디가 겹쳐 실패한다(로컬 DB를 지우고 다시).
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "pw01.ranking.seed.accounts")
@RequiredArgsConstructor
public class RankingDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RankingDemoSeeder.class);
    private static final String NOT_A_PASSWORD_HASH = "seed-account-cannot-login";

    private final AccountService accountService;
    private final RankingRecordService recordService;
    private final StageCatalog stageCatalog;
    private final TransactionTemplate transactionTemplate;

    @Value("${pw01.ranking.seed.accounts}")
    private int accounts;

    @Value("${pw01.ranking.seed.clears-per-account:4}")
    private int clearsPerAccount;

    @Override
    public void run(ApplicationArguments args) {
        List<StageDef> stages = stageCatalog.stages();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long started = System.nanoTime();
        for (int i = 1; i <= accounts; i++) {
            String id = "seed%04d".formatted(i);
            long accountId = accountService.register(id, NOT_A_PASSWORD_HASH, "시드" + i, null).getId();
            for (int c = 0; c < clearsPerAccount; c++) {
                StageDef stage = stages.get(random.nextInt(stages.size()));
                long recommendedMs = stage.recommendedClearSeconds() * 1000L;
                long playTimeMs = random.nextLong(recommendedMs / 5, recommendedMs * 3 / 2);
                Instant at = now.minus(Duration.ofMinutes(random.nextLong(20L * 24 * 60)));
                transactionTemplate.executeWithoutResult(status -> recordService.recordClear(new RankingClear(
                        accountId, UUID.randomUUID().toString(), stage.stageId(), 0, true, playTimeMs, at)));
            }
        }
        log.info("랭킹 시드 완료 accounts={} clearsPerAccount={} {}ms", accounts, clearsPerAccount,
                (System.nanoTime() - started) / 1_000_000);
    }

}
