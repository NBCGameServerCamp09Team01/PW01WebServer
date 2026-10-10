package com.pw01.webserver.account.service;

import com.pw01.webserver.account.master.LevelCurveFile;
import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.common.master.MasterDataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 레벨 곡선과 결과 경험치: master/level-curve.json을 기동 때 읽고 검사해 들고 있다(틀리면 기동 실패).
 * 계산 원본은 총 경험치다(지시서 K8-1d). 레벨과 "지금 레벨 안의 경험치"는 총 경험치에서 다시 계산한다.
 * 레벨 업 규칙은 UE UWarriorAccountSubsystem::GrantExperience와 같다(K8-2a): 필요 경험치 = expBase + (L - 1) × expStep,
 * 최대 레벨에서는 지금 레벨 안의 경험치 0. 값은 기동 뒤 바뀌지 않는다.
 */
@Component
public class LevelCurve {

    static final String FILE_NAME = "level-curve.json";

    private final LevelCurveFile curve;

    @Autowired
    public LevelCurve(MasterDataLoader loader) {
        this(loader.load(FILE_NAME, LevelCurveFile.class));
    }

    /** 시험용: 파일 없이 값으로 만든다(검사는 같다) */
    LevelCurve(LevelCurveFile curve) {
        validate(curve);
        this.curve = curve;
    }

    /** 결과의 경험치: 클리어면 clearedExp, 아니면 failedExp */
    public int resultExp(boolean cleared) {
        return cleared ? curve.clearedExp() : curve.failedExp();
    }

    /** 레벨 L에서 다음 레벨까지 필요한 경험치. 최대 레벨이면 0 */
    public long expToNext(int level) {
        if (level >= curve.maxLevel()) {
            return 0;
        }
        return curve.expBase() + (long) (level - 1) * curve.expStep();
    }

    /**
     * 총 경험치에 exp를 더하고 레벨을 다시 계산한다(저장하지 않음).
     *
     * @param levelBefore 지금 레벨(오른 레벨 수를 세는 기준)
     * @param totalBefore 지금 총 경험치
     */
    public LevelGain gain(int levelBefore, long totalBefore, int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("경험치는 0 이상이어야 합니다: " + exp);
        }
        long totalAfter = totalBefore + exp;
        int level = 1;
        long remaining = totalAfter;
        while (level < curve.maxLevel() && remaining >= expToNext(level)) {
            remaining -= expToNext(level);
            level++;
        }
        int experience = level >= curve.maxLevel() ? 0 : (int) remaining;
        int levelsUp = Math.max(0, level - levelBefore);
        return new LevelGain(exp, levelBefore, level, experience, totalAfter, levelsUp * curve.statPointsPerLevel());
    }

    private static void validate(LevelCurveFile curve) {
        if (curve.maxLevel() < 1) {
            throw fail("maxLevel은 1 이상이어야 합니다: " + curve.maxLevel());
        }
        if (curve.clearedExp() < 1 || curve.failedExp() < 0) {
            throw fail("clearedExp는 1 이상, failedExp는 0 이상이어야 합니다.");
        }
        if (curve.failedExp() > curve.clearedExp()) {
            throw fail("failedExp가 clearedExp보다 큽니다.");
        }
        if (curve.statPointsPerLevel() < 1) {
            throw fail("statPointsPerLevel은 1 이상이어야 합니다: " + curve.statPointsPerLevel());
        }
        if (curve.expBase() < 1 || curve.expStep() < 0) {
            throw fail("expBase는 1 이상, expStep은 0 이상이어야 합니다.");
        }
    }

    private static MasterDataException fail(String reason) {
        return new MasterDataException(FILE_NAME, reason);
    }

}
