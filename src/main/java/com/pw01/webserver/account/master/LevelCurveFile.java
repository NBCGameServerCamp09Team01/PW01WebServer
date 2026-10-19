package com.pw01.webserver.account.master;

/**
 * master/level-curve.json의 모양. 처음 값은 UE UWarriorAccountSubsystem::FRules와 같다(결과가 게임 시연과 같게).
 * 빠진 숫자 칸은 0으로 들어오므로 LevelCurve가 범위(≥ 1)를 검사한다.
 *
 * @param maxLevel           최대 레벨. 최대 레벨에서는 지금 레벨 안의 경험치를 0으로 둔다
 * @param clearedExp         클리어 경험치
 * @param failedExp          실패 경험치
 * @param statPointsPerLevel 레벨 하나 오를 때 받는 스탯 포인트
 * @param expBase            레벨 1 → 2에 필요한 경험치
 * @param expStep            레벨이 하나 오를 때마다 필요한 경험치가 늘어나는 양. 레벨 L → L+1 = expBase + (L - 1) × expStep
 */
public record LevelCurveFile(int maxLevel, int clearedExp, int failedExp, int statPointsPerLevel,
                             int expBase, int expStep) {
}
