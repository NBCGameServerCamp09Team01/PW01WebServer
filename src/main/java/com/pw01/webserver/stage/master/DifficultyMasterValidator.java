package com.pw01.webserver.stage.master;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.stage.master.DifficultyMasterFile.DifficultyEntry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * difficulties.json 검사(stage-api.md "서버 기동 검사"). 하나라도 걸리면 MasterDataException → 기동 실패.
 * D1 version ≥ 1 / D2 하나 이상, 번호가 0부터 빈틈없이(파일 순서 = 번호 순) / D3 이름 있음
 * D4 배율 네 개 > 0, 점수 배수 ≥ 1 / D5 난이도 0은 배율 모두 1.0·점수 배수 100(조절 없음)
 */
public final class DifficultyMasterValidator {

    static final String FILE = "difficulties.json";

    private DifficultyMasterValidator() {
    }

    /** @return 받는 난이도 번호(0부터 순서대로) */
    public static Set<Integer> validate(DifficultyMasterFile file) {
        if (file.version() < 1) {
            throw fail("version은 1 이상이어야 합니다.");
        }
        List<DifficultyEntry> entries = file.difficulties();
        if (entries == null || entries.isEmpty()) {
            throw fail("난이도가 하나도 없습니다.");
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            DifficultyEntry entry = entries.get(i);
            if (entry.difficulty() == null || entry.difficulty() != i) {
                throw fail("difficulty는 0부터 빈틈없이 순서대로여야 합니다(" + i + "번째 줄): " + entry.difficulty());
            }
            String where = "difficulty " + i;
            if (entry.name() == null || entry.name().isBlank()) {
                throw fail("name이 비어 있습니다: " + where);
            }
            if (!isPositive(entry.enemyCountMultiplier()) || !isPositive(entry.enemyHealthMultiplier())
                    || !isPositive(entry.enemyDamageMultiplier()) || !isPositive(entry.goldAmountMultiplier())) {
                throw fail("배율은 0보다 커야 합니다: " + where);
            }
            if (entry.scoreMultiplierPercent() == null || entry.scoreMultiplierPercent() < 1) {
                throw fail("scoreMultiplierPercent는 1 이상이어야 합니다: " + where);
            }
            if (i == 0 && !isNoAdjustment(entry)) {
                throw fail("난이도 0은 조절 없음이라 배율이 모두 1.0, scoreMultiplierPercent가 100이어야 합니다.");
            }
            ids.add(i);
        }
        return ids;
    }

    /** 스테이지 덮어쓰기(StageWaveMasterValidator)도 같은 범위 검사를 쓴다 */
    static boolean isPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }

    private static boolean isNoAdjustment(DifficultyEntry entry) {
        return entry.enemyCountMultiplier() == 1.0 && entry.enemyHealthMultiplier() == 1.0
                && entry.enemyDamageMultiplier() == 1.0 && entry.goldAmountMultiplier() == 1.0
                && entry.scoreMultiplierPercent() == 100;
    }

    private static MasterDataException fail(String reason) {
        return new MasterDataException(FILE, reason);
    }

}
