package com.pw01.webserver.stage.master;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.stage.master.MonsterMasterFile.MonsterEntry;
import com.pw01.webserver.stage.master.StageMasterFile.RegionEntry;
import com.pw01.webserver.stage.master.StageMasterFile.StageEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.EnemyEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.MultiplierOverride;
import com.pw01.webserver.stage.master.StageWaveMasterFile.StageWavesEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.WaveEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * monsters.json·stage-waves.json 검사(stage-api.md "서버 기동 검사"). 하나라도 걸리면 MasterDataException → 기동 실패.
 * stages.json은 StageMasterValidator가 먼저 검사했다고 본다.
 * 몬스터: M1 하나 이상 / M2 키 모양·중복 / M3 이름 / M4 boss·goldReward(≥ 0)·goldDropChance(0~1) 있음 / M5 version ≥ 1
 * 웨이브: W1 version ≥ 1 / W2 stages.json의 모든 스테이지가 정확히 한 번, 모르는 스테이지 없음 / W3 waves 1개 이상이고 수 = waveCount
 *        W4 웨이브마다 적 1줄 이상, 쉬는 시간·배율 ≥ 0 / W5 적: 아는 monsterId, count ≥ 1, 덮어쓴 골드·확률 범위, spawnGroup은 비어 있지 않음
 * 난이도(M9): W6 countByDifficulty 키는 있는 난이도이고 1 이상, 값 ≥ 1(0이면 난이도마다 몬스터 종류가 달라짐)
 *        W7 difficultyMultipliers 키는 있는 난이도이고 1 이상(0은 조절 없음), 적힌 배율 > 0
 */
public final class StageWaveMasterValidator {

    static final String MONSTERS_FILE = "monsters.json";
    static final String WAVES_FILE = "stage-waves.json";

    /** monster.으로 시작, 영문 소문자·숫자·점, 1~64자(stageId와 같은 규칙에 접두어) */
    private static final Pattern MONSTER_KEY = Pattern.compile("^monster\\.[a-z0-9.]{1,56}$");

    private StageWaveMasterValidator() {
    }

    /** 난이도가 0 하나뿐인 경우(M24 시험용) */
    public static void validate(StageMasterFile stages, StageWaveMasterFile waves, MonsterMasterFile monsters) {
        validate(stages, waves, monsters, Set.of(0));
    }

    /** @param difficultyIds 받는 난이도 번호(DifficultyMasterValidator가 검사한 것) */
    public static void validate(StageMasterFile stages, StageWaveMasterFile waves, MonsterMasterFile monsters,
                                Set<Integer> difficultyIds) {
        Set<String> monsterIds = validateMonsters(monsters);
        validateWaves(stages, waves, monsterIds, difficultyIds);
    }

    private static Set<String> validateMonsters(MonsterMasterFile file) {
        if (file.version() < 1) {
            throw monsters("version은 1 이상이어야 합니다.");
        }
        if (file.monsters() == null || file.monsters().isEmpty()) {
            throw monsters("몬스터가 하나도 없습니다.");
        }
        Set<String> ids = new HashSet<>();
        for (MonsterEntry monster : file.monsters()) {
            String id = monster.monsterId();
            if (id == null || !MONSTER_KEY.matcher(id).matches()) {
                throw monsters("몬스터 키 모양이 틀렸습니다(monster.으로 시작, 영문 소문자·숫자·점, 1~64자): " + id);
            }
            if (!ids.add(id)) {
                throw monsters("몬스터 키가 겹칩니다: " + id);
            }
            if (monster.name() == null || monster.name().isBlank()) {
                throw monsters("name이 비어 있습니다: " + id);
            }
            if (monster.boss() == null) {
                throw monsters("boss가 없습니다: " + id);
            }
            if (monster.goldReward() == null || monster.goldReward() < 0) {
                throw monsters("goldReward는 0 이상이어야 합니다: " + id);
            }
            if (!isChance(monster.goldDropChance())) {
                throw monsters("goldDropChance는 0~1이어야 합니다: " + id);
            }
        }
        return ids;
    }

    private static void validateWaves(StageMasterFile stageFile, StageWaveMasterFile file, Set<String> monsterIds,
                                      Set<Integer> difficultyIds) {
        if (file.version() < 1) {
            throw waves("version은 1 이상이어야 합니다.");
        }
        if (file.stages() == null || file.stages().isEmpty()) {
            throw waves("스테이지가 하나도 없습니다.");
        }

        Map<String, StageEntry> defined = new HashMap<>();
        for (RegionEntry region : stageFile.regions()) {
            region.stages().forEach(stage -> defined.put(stage.stageId(), stage));
        }

        Set<String> seen = new HashSet<>();
        for (StageWavesEntry entry : file.stages()) {
            String stageId = entry.stageId();
            StageEntry stage = defined.get(stageId);
            if (stage == null) {
                throw waves("stages.json에 없는 스테이지입니다: " + stageId);
            }
            if (!seen.add(stageId)) {
                throw waves("같은 스테이지가 두 번 있습니다: " + stageId);
            }
            if (entry.waves() == null || entry.waves().isEmpty()) {
                throw waves("웨이브가 없습니다: " + stageId);
            }
            if (entry.waves().size() != stage.waveCount()) {
                throw waves("웨이브 수(" + entry.waves().size() + ")가 stages.json의 waveCount(" + stage.waveCount()
                        + ")와 다릅니다: " + stageId);
            }
            validateOverrides(entry.difficultyMultipliers(), stageId, difficultyIds);
            for (int i = 0; i < entry.waves().size(); i++) {
                validateWave(entry.waves().get(i), stageId + " 웨이브 " + (i + 1), monsterIds, difficultyIds);
            }
        }

        for (String stageId : defined.keySet()) {
            if (!seen.contains(stageId)) {
                throw waves("웨이브 구성이 없는 스테이지입니다: " + stageId);
            }
        }
    }

    private static void validateOverrides(Map<String, MultiplierOverride> overrides, String stageId,
                                          Set<Integer> difficultyIds) {
        if (overrides == null) {
            return;
        }
        for (Entry<String, MultiplierOverride> entry : overrides.entrySet()) {
            String where = stageId + " difficultyMultipliers \"" + entry.getKey() + "\"";
            checkDifficultyKey(entry.getKey(), difficultyIds, where);
            MultiplierOverride value = entry.getValue();
            if (value == null) {
                throw waves("배율 덮어쓰기가 비어 있습니다: " + where);
            }
            if (isNotPositive(value.enemyCountMultiplier()) || isNotPositive(value.enemyHealthMultiplier())
                    || isNotPositive(value.enemyDamageMultiplier()) || isNotPositive(value.goldAmountMultiplier())) {
                throw waves("덮어쓴 배율은 0보다 커야 합니다: " + where);
            }
        }
    }

    /** 난이도 키: 숫자, 1 이상(0은 기본 칸이 맡음), 있는 난이도 */
    private static void checkDifficultyKey(String key, Set<Integer> difficultyIds, String where) {
        int difficulty;
        try {
            difficulty = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            throw waves("난이도 키는 숫자여야 합니다: " + where);
        }
        if (difficulty < 1) {
            throw waves("난이도 키는 1 이상이어야 합니다(0은 기본 값): " + where);
        }
        if (!difficultyIds.contains(difficulty)) {
            throw waves("difficulties.json에 없는 난이도입니다: " + where);
        }
    }

    private static void validateWave(WaveEntry wave, String where, Set<String> monsterIds, Set<Integer> difficultyIds) {
        if (wave.enemies() == null || wave.enemies().isEmpty()) {
            throw waves("적이 없습니다: " + where);
        }
        if (isNegative(wave.restSeconds()) || isNegative(wave.goldChanceMultiplier())
                || isNegative(wave.goldAmountMultiplier())) {
            throw waves("restSeconds·배율은 0 이상이어야 합니다: " + where);
        }
        for (EnemyEntry enemy : wave.enemies()) {
            String id = enemy.monsterId();
            if (id == null || !monsterIds.contains(id)) {
                throw waves("monsters.json에 없는 몬스터입니다: " + where + " → " + id);
            }
            if (enemy.count() == null || enemy.count() < 1) {
                throw waves("count는 1 이상이어야 합니다: " + where + " → " + id);
            }
            if (enemy.goldReward() != null && enemy.goldReward() < 0) {
                throw waves("goldReward는 0 이상이어야 합니다: " + where + " → " + id);
            }
            if (enemy.goldDropChance() != null && !isChance(enemy.goldDropChance())) {
                throw waves("goldDropChance는 0~1이어야 합니다: " + where + " → " + id);
            }
            if (enemy.spawnGroup() != null && enemy.spawnGroup().isBlank()) {
                throw waves("spawnGroup이 비어 있습니다(없으면 칸을 빼기): " + where + " → " + id);
            }
            if (enemy.countByDifficulty() != null) {
                for (Entry<String, Integer> count : enemy.countByDifficulty().entrySet()) {
                    String countWhere = where + " → " + id + " countByDifficulty \"" + count.getKey() + "\"";
                    checkDifficultyKey(count.getKey(), difficultyIds, countWhere);
                    if (count.getValue() == null || count.getValue() < 1) {
                        throw waves("countByDifficulty 값은 1 이상이어야 합니다(몬스터 종류는 난이도와 상관없이 같음): "
                                + countWhere);
                    }
                }
            }
        }
    }

    private static boolean isChance(Double value) {
        return value != null && value >= 0 && value <= 1;
    }

    /** 덮어쓰기 칸: 비어 있으면 괜찮고, 있으면 0보다 커야 함 */
    private static boolean isNotPositive(Double value) {
        return value != null && !DifficultyMasterValidator.isPositive(value);
    }

    private static boolean isNegative(Double value) {
        return value != null && value < 0;
    }

    private static MasterDataException monsters(String reason) {
        return new MasterDataException(MONSTERS_FILE, reason);
    }

    private static MasterDataException waves(String reason) {
        return new MasterDataException(WAVES_FILE, reason);
    }

}
