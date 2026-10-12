package com.pw01.webserver.stageresult.service;

/**
 * 한 계정의 결과를 모은 값(결과 표가 원천). 누적 통계(account_stat_total)와 견줄 때 쓴다.
 *
 * @param played      결과 수
 * @param cleared     클리어 수
 * @param kills       처치 합계
 * @param gold        골드 합계
 * @param playTimeMs  플레이 시간 합계(밀리초)
 * @param maxWave     가장 멀리 간 웨이브
 * @param bestClearMs 가장 빠른 클리어(밀리초). 클리어한 적이 없으면 null
 */
public record ResultTotals(long played, long cleared, long kills, long gold, long playTimeMs, long maxWave,
                           Long bestClearMs) {
}
