package com.pw01.webserver.account.service;

/**
 * 경험치를 더한 계산 결과(저장 전). LevelCurve.gain이 만들고 AccountProgress.apply가 그대로 반영한다.
 *
 * @param expGained        이번에 더한 경험치
 * @param levelBefore      더하기 전 레벨
 * @param levelAfter       더한 뒤 레벨
 * @param experienceAfter  더한 뒤 지금 레벨 안의 경험치(최대 레벨이면 0)
 * @param totalAfter       더한 뒤 총 경험치(계산 원본)
 * @param statPointsGained 오른 레벨 수 × 레벨당 스탯 포인트
 */
public record LevelGain(int expGained, int levelBefore, int levelAfter, int experienceAfter, long totalAfter,
                        int statPointsGained) {
}
