package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 누적 통계 대조 설정 값(pw01.stat-total.reconcile.*). 기본값은 application.properties에 있다. 틀린 값이면 서버가 뜨지 않는다.
 * 대조 = 결과 표(stage_play_result, 원천)를 계정별로 다시 모아 누적 통계(account_stat_total)가 어긋났으면 고친다(StatTotalReconciler).
 *
 * @param enabled   주기 대조를 켤지
 * @param interval  대조 사이 간격(서버가 뜬 뒤 이 시간이 지나 처음 돈다)
 * @param batchSize 한 트랜잭션에서 보는 계정 수
 */
@ConfigurationProperties("pw01.stat-total.reconcile")
public record StatTotalProperties(boolean enabled, Duration interval, int batchSize) {

    public StatTotalProperties {
        if (interval == null || interval.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("pw01.stat-total.reconcile.interval은 1초 이상이어야 합니다: " + interval);
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("pw01.stat-total.reconcile.batch-size는 1 이상이어야 합니다: " + batchSize);
        }
    }

}
