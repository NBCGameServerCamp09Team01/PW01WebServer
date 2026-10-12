package com.pw01.webserver.account.service;

import com.pw01.webserver.account.repository.AccountRepository;
import com.pw01.webserver.account.repository.AccountStatTotalRepository;
import com.pw01.webserver.config.StatTotalProperties;
import com.pw01.webserver.stageresult.service.ResultTotals;
import com.pw01.webserver.stageresult.service.StageResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 누적 통계 대조(지시서 1-5c): 결과 표(stage_play_result, 원천)를 계정별로 다시 모아 누적 통계(account_stat_total)와 견주고,
 * 어긋났으면 결과 표 값으로 고친다(경고 로그 + 고친 줄 수). 누적 통계는 결과 저장과 같은 트랜잭션에서 바뀌므로 보통은 어긋나지 않는다.
 * <p>
 * 계정을 batch-size씩 한 트랜잭션에서 본다: 두 표를 같은 시점(REPEATABLE READ)으로 읽고, 고칠 때는 "읽은 값 그대로일 때만" 바꾼다.
 * 그사이 새 결과가 누적 통계를 바꿨으면 건드리지 않고 다음 대조에 맡긴다(새 결과를 지우지 않게).
 * <p>
 * 앱 전체의 @EnableScheduling을 켜지 않고 전용 스케줄러를 쓴다(RealtimeHeartbeat와 같은 이유).
 */
@Service
public class StatTotalReconciler implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StatTotalReconciler.class);

    /** 합계 키: 결과가 없거나 0이면 줄이 없어도 된다 */
    private static final List<String> SUM_KEYS = List.of(AccountService.STAT_STAGE_PLAY_PLAYED,
            AccountService.STAT_STAGE_PLAY_CLEARED, AccountService.STAT_STAGE_PLAY_KILLS,
            AccountService.STAT_STAGE_PLAY_GOLD, AccountService.STAT_STAGE_PLAY_PLAY_TIME_MS);
    /** 최고 기록 키: 기록이 없으면 줄이 없어야 한다 */
    private static final List<String> RECORD_KEYS = List.of(AccountService.STAT_STAGE_PLAY_MAX_WAVE,
            AccountService.STAT_STAGE_PLAY_BEST_CLEAR_MS);

    private final AccountRepository accountRepository;
    private final AccountStatTotalRepository accountStatTotalRepository;
    private final StageResultService stageResultService;
    private final StatTotalProperties properties;
    private final TransactionTemplate transactionTemplate;
    private ThreadPoolTaskScheduler scheduler;

    public StatTotalReconciler(AccountRepository accountRepository, AccountStatTotalRepository accountStatTotalRepository,
                               StageResultService stageResultService, StatTotalProperties properties,
                               TransactionTemplate transactionTemplate) {
        this.accountRepository = accountRepository;
        this.accountStatTotalRepository = accountStatTotalRepository;
        this.stageResultService = stageResultService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.enabled()) {
            log.info("누적 통계 대조 꺼짐(pw01.stat-total.reconcile.enabled=false)");
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stat-total-reconcile-");
        scheduler.initialize();
        Duration interval = properties.interval();
        scheduler.scheduleWithFixedDelay(this::runSafely, Instant.now().plus(interval), interval);
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /** 모든 계정을 한 번 대조한다. 고친 줄 수를 돌려준다(시험이 직접 부른다) */
    public int reconcileAll() {
        int fixed = 0;
        int accounts = 0;
        long after = 0;
        while (true) {
            List<Long> ids = accountRepository.findIdsAfter(after, PageRequest.of(0, properties.batchSize()));
            if (ids.isEmpty()) {
                break;
            }
            Integer batchFixed = transactionTemplate.execute(status -> reconcileBatch(ids));
            fixed += batchFixed == null ? 0 : batchFixed;
            accounts += ids.size();
            after = ids.get(ids.size() - 1);
        }
        if (fixed > 0) {
            log.warn("누적 통계 대조: 어긋난 {}줄을 결과 표 값으로 고침(계정 {}개 확인)", fixed, accounts);
        } else {
            log.info("누적 통계 대조: 어긋남 없음(계정 {}개 확인)", accounts);
        }
        return fixed;
    }

    private void runSafely() {
        try {
            reconcileAll();
        } catch (Exception e) {
            log.error("누적 통계 대조 실패(다음 주기에 다시)", e);
        }
    }

    private int reconcileBatch(List<Long> accountIds) {
        Map<Long, ResultTotals> expectedByAccount = stageResultService.totalsByAccount(accountIds);
        Map<Long, Map<String, Long>> actualByAccount = accountStatTotalRepository.totals(accountIds);
        int fixed = 0;
        for (Long accountId : accountIds) {
            Map<String, Long> expected = expected(expectedByAccount.get(accountId));
            Map<String, Long> actual = actualByAccount.getOrDefault(accountId, Map.of());
            for (String key : SUM_KEYS) {
                long want = expected.getOrDefault(key, 0L);
                Long have = actual.get(key);
                if ((have == null ? 0 : have) != want) {
                    fixed += fix(accountId, key, have, want);
                }
            }
            for (String key : RECORD_KEYS) {
                Long want = expected.get(key);
                Long have = actual.get(key);
                if (!Objects.equals(have, want)) {
                    fixed += fix(accountId, key, have, want);
                }
            }
        }
        return fixed;
    }

    private int fix(Long accountId, String key, Long have, Long want) {
        int rows = accountStatTotalRepository.replaceIf(accountId, key, have, want);
        if (rows > 0) {
            log.warn("누적 통계 고침 accountId={} key={} {} → {}", accountId, key, have, want);
        }
        return rows;
    }

    /** 결과 표에서 모은 값 → 누적 통계 키. 결과가 없으면 빈 값(합계 0, 기록 없음) */
    private static Map<String, Long> expected(ResultTotals totals) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (totals == null) {
            return map;
        }
        map.put(AccountService.STAT_STAGE_PLAY_PLAYED, totals.played());
        map.put(AccountService.STAT_STAGE_PLAY_CLEARED, totals.cleared());
        map.put(AccountService.STAT_STAGE_PLAY_KILLS, totals.kills());
        map.put(AccountService.STAT_STAGE_PLAY_GOLD, totals.gold());
        map.put(AccountService.STAT_STAGE_PLAY_PLAY_TIME_MS, totals.playTimeMs());
        map.put(AccountService.STAT_STAGE_PLAY_MAX_WAVE, totals.maxWave());
        if (totals.bestClearMs() != null) {
            map.put(AccountService.STAT_STAGE_PLAY_BEST_CLEAR_MS, totals.bestClearMs());
        }
        return map;
    }

}
