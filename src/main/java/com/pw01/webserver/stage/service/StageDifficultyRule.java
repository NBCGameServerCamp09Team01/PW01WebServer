package com.pw01.webserver.stage.service;

import com.pw01.webserver.stage.master.StageWaveMasterFile.MultiplierOverride;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * 난이도 계산 규칙(계산만, stage-api.md "난이도"). StageCatalog가 기동 때 스테이지·난이도마다 한 번 부른다.
 * 몬스터 종류(줄)는 바꾸지 않고 마리 수와 배율만 정한다.
 */
public final class StageDifficultyRule {

    private StageDifficultyRule() {
    }

    /**
     * 그 스테이지의 배율. 칸마다 스테이지 덮어쓰기가 있으면 그 값, 없으면 difficulties.json 값(칸 단위).
     *
     * @param base     difficulties.json의 그 난이도 배율
     * @param override 그 스테이지의 그 난이도 덮어쓰기. 없으면 null
     */
    public static DifficultyMultipliers multipliers(DifficultyMultipliers base, MultiplierOverride override) {
        if (override == null) {
            return base;
        }
        return new DifficultyMultipliers(
                Objects.requireNonNullElse(override.enemyCountMultiplier(), base.enemyCountMultiplier()),
                Objects.requireNonNullElse(override.enemyHealthMultiplier(), base.enemyHealthMultiplier()),
                Objects.requireNonNullElse(override.enemyDamageMultiplier(), base.enemyDamageMultiplier()),
                Objects.requireNonNullElse(override.goldAmountMultiplier(), base.goldAmountMultiplier()));
    }

    /**
     * 적 한 줄의 난이도 d 마리 수.
     * <ul>
     *   <li>countByDifficulty가 있음(직접 수 줄): d 이하에서 가장 큰 키의 값, 없으면 count. 배율을 곱하지 않는다(계단식)</li>
     *   <li>없음(배율 줄): 보스 줄은 count 그대로, 나머지는 max(1, round(count × 수 배율)), 반올림은 0.5 올림</li>
     * </ul>
     *
     * @param count             기본 마리 수(난이도 0의 수, 1 이상)
     * @param boss              이 줄이 보스인가(덮어쓰기 반영)
     * @param countByDifficulty 난이도 번호 문자열 → 마리 수. 없으면 null
     * @param difficulty        난이도 번호(0 이상)
     * @param countMultiplier   그 스테이지의 수 배율
     */
    public static int count(int count, boolean boss, Map<String, Integer> countByDifficulty, int difficulty,
                            double countMultiplier) {
        if (countByDifficulty != null && !countByDifficulty.isEmpty()) {
            int value = count;
            int bestKey = 0;
            for (Map.Entry<String, Integer> entry : countByDifficulty.entrySet()) {
                int key = Integer.parseInt(entry.getKey());
                if (key <= difficulty && key > bestKey) {
                    bestKey = key;
                    value = entry.getValue();
                }
            }
            return value;
        }
        if (boss) {
            return count;
        }
        return Math.max(1, BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(countMultiplier))
                .setScale(0, RoundingMode.HALF_UP).intValueExact());
    }

}
