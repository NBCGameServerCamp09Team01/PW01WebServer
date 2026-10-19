package com.pw01.webserver.stage.master;

import com.pw01.webserver.common.master.MasterDataException;
import com.pw01.webserver.stage.master.MonsterMasterFile.MonsterEntry;
import com.pw01.webserver.stage.master.StageMasterFile.RegionEntry;
import com.pw01.webserver.stage.master.StageMasterFile.StageEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.EnemyEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.MultiplierOverride;
import com.pw01.webserver.stage.master.StageWaveMasterFile.StageWavesEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.WaveEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * monsters.json·stage-waves.json 검사 규칙(M1~M5, W1~W5) 단위 테스트. 파일 대신 record를 코드로 만든다.
 * 규칙마다 틀린 경우 하나씩 넣어 기동 실패 예외, 걸린 파일 이름, 걸린 키가 메시지에 있는지 본다.
 * 실패하면 StageWaveMasterValidator의 해당 규칙을 의심한다.
 */
class StageWaveMasterValidatorTest {

    private static final StageMasterFile STAGES = new StageMasterFile(List.of(new RegionEntry("region.01", "지역", 1,
            List.of(new StageEntry("stage.01.01", "스테이지 1", 1, null, 2, 300),
                    new StageEntry("stage.01.02", "스테이지 2", 2, "stage.01.01", 1, 300)))));

    private static MonsterEntry monster(String id) {
        return new MonsterEntry(id, "몬스터", false, 2, 1.0);
    }

    private static final MonsterMasterFile MONSTERS = new MonsterMasterFile(1, List.of(
            monster("monster.samurai"), new MonsterEntry("monster.boss.warrior", "보스", true, 0, 1.0)));

    private static EnemyEntry enemy(String monsterId, Integer count) {
        return new EnemyEntry(monsterId, count, null, null, null, null);
    }

    private static WaveEntry wave(EnemyEntry... enemies) {
        return new WaveEntry(null, null, null, null, List.of(enemies));
    }

    /** stage.01.01은 웨이브 2개(waveCount 2), stage.01.02는 1개(waveCount 1) */
    private static StageWaveMasterFile waves(WaveEntry firstStageSecondWave) {
        return new StageWaveMasterFile(1, List.of(
                new StageWavesEntry("stage.01.01", List.of(wave(enemy("monster.samurai", 2)), firstStageSecondWave)),
                new StageWavesEntry("stage.01.02", List.of(wave(enemy("monster.samurai", 3))))));
    }

    private static final StageWaveMasterFile OK_WAVES = waves(wave(enemy("monster.boss.warrior", 1)));

    private static void assertFails(StageWaveMasterFile waves, MonsterMasterFile monsters, String file, String part) {
        assertThatThrownBy(() -> StageWaveMasterValidator.validate(STAGES, waves, monsters))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("[master/" + file + "]")
                .hasMessageContaining(part);
    }

    // 확인: 덮어쓰기(골드 0·보스)와 기본값(빈 칸)이 섞인 정상 모양은 통과
    @Test
    void 정상() {
        StageWaveMasterFile waves = waves(new WaveEntry(10.0, true, 1.5, 0.5, List.of(
                enemy("monster.boss.warrior", 1),
                new EnemyEntry("monster.samurai", 2, "left", false, 0, 0.5))));
        assertThatCode(() -> StageWaveMasterValidator.validate(STAGES, waves, MONSTERS)).doesNotThrowAnyException();
    }

    // M1·M5: 몬스터가 없음, version 0
    @Test
    void 몬스터_없음과_버전() {
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of()), "monsters.json", "몬스터가 하나도 없습니다");
        assertFails(OK_WAVES, new MonsterMasterFile(0, MONSTERS.monsters()), "monsters.json", "version");
    }

    // M2·M3: 키 모양(접두어 monster. 필요, 대문자 금지), 중복, 빈 이름
    @Test
    void 몬스터_키와_이름() {
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(monster("samurai"))), "monsters.json", "samurai");
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(monster("monster.Samurai"))), "monsters.json",
                "monster.Samurai");
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(monster("monster.samurai"), monster("monster.samurai"))),
                "monsters.json", "겹칩니다");
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(new MonsterEntry("monster.samurai", " ", false, 2, 1.0))),
                "monsters.json", "name");
    }

    // M4: 빠진 칸(null), 음수 골드, 0~1 밖 확률
    @Test
    void 몬스터_값_범위() {
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(new MonsterEntry("monster.samurai", "a", null, 2, 1.0))),
                "monsters.json", "boss");
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(new MonsterEntry("monster.samurai", "a", false, -1, 1.0))),
                "monsters.json", "goldReward");
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(new MonsterEntry("monster.samurai", "a", false, 2, 1.5))),
                "monsters.json", "goldDropChance");
        assertFails(OK_WAVES, new MonsterMasterFile(1, List.of(new MonsterEntry("monster.samurai", "a", false, 2, null))),
                "monsters.json", "goldDropChance");
    }

    // W1: 웨이브 파일 version 0
    @Test
    void 웨이브_파일_버전() {
        assertFails(new StageWaveMasterFile(0, OK_WAVES.stages()), MONSTERS, "stage-waves.json", "version");
    }

    // W2: stages.json에 없는 스테이지, 같은 스테이지 두 번, 웨이브 구성이 없는 스테이지
    @Test
    void 두_파일의_스테이지_맞춤() {
        List<StageWavesEntry> ok = OK_WAVES.stages();
        assertFails(new StageWaveMasterFile(1, List.of(ok.get(0), ok.get(1),
                        new StageWavesEntry("stage.09.09", List.of(wave(enemy("monster.samurai", 1)))))),
                MONSTERS, "stage-waves.json", "stage.09.09");
        assertFails(new StageWaveMasterFile(1, List.of(ok.get(0), ok.get(1), ok.get(1))),
                MONSTERS, "stage-waves.json", "두 번");
        assertFails(new StageWaveMasterFile(1, List.of(ok.get(0))), MONSTERS, "stage-waves.json", "stage.01.02");
    }

    // W3: 웨이브 없음, 웨이브 수 ≠ stages.json waveCount(체크포인트 범위가 waveCount라 반드시 같아야 함)
    @Test
    void 웨이브_수() {
        assertFails(new StageWaveMasterFile(1, List.of(new StageWavesEntry("stage.01.01", List.of()),
                OK_WAVES.stages().get(1))), MONSTERS, "stage-waves.json", "웨이브가 없습니다");
        assertFails(new StageWaveMasterFile(1, List.of(
                        new StageWavesEntry("stage.01.01", List.of(wave(enemy("monster.samurai", 1)))),
                        OK_WAVES.stages().get(1))),
                MONSTERS, "stage-waves.json", "waveCount");
    }

    // W4: 적이 없는 웨이브, 음수 쉬는 시간·배율
    @Test
    void 웨이브_값() {
        assertFails(waves(new WaveEntry(null, null, null, null, List.of())), MONSTERS, "stage-waves.json",
                "stage.01.01 웨이브 2");
        assertFails(waves(new WaveEntry(-1.0, null, null, null, List.of(enemy("monster.samurai", 1)))), MONSTERS,
                "stage-waves.json", "restSeconds");
        assertFails(waves(new WaveEntry(null, null, -0.1, null, List.of(enemy("monster.samurai", 1)))), MONSTERS,
                "stage-waves.json", "배율");
    }

    // W5: 모르는 몬스터, count 0·빠짐, 덮어쓴 골드·확률 범위, 빈 spawnGroup
    @Test
    void 적_값() {
        assertFails(waves(wave(enemy("monster.unknown", 1))), MONSTERS, "stage-waves.json", "monster.unknown");
        assertFails(waves(wave(enemy("monster.samurai", 0))), MONSTERS, "stage-waves.json", "count");
        assertFails(waves(wave(enemy("monster.samurai", null))), MONSTERS, "stage-waves.json", "count");
        assertFails(waves(wave(new EnemyEntry("monster.samurai", 1, null, null, -1, null))), MONSTERS,
                "stage-waves.json", "goldReward");
        assertFails(waves(wave(new EnemyEntry("monster.samurai", 1, null, null, null, 2.0))), MONSTERS,
                "stage-waves.json", "goldDropChance");
        assertFails(waves(wave(new EnemyEntry("monster.samurai", 1, " ", null, null, null))), MONSTERS,
                "stage-waves.json", "spawnGroup");
    }

    // ---- 난이도(M9): W6 countByDifficulty, W7 difficultyMultipliers. 난이도는 0·1·2가 있다고 본다 ----

    private static final Set<Integer> DIFFICULTIES = Set.of(0, 1, 2);

    private static EnemyEntry byDifficulty(Map<String, Integer> counts) {
        return new EnemyEntry("monster.samurai", 2, counts, null, null, null, null);
    }

    private static StageWaveMasterFile withOverride(Map<String, MultiplierOverride> overrides) {
        return new StageWaveMasterFile(1, List.of(
                new StageWavesEntry("stage.01.01", overrides,
                        List.of(wave(enemy("monster.samurai", 2)), wave(enemy("monster.boss.warrior", 1)))),
                new StageWavesEntry("stage.01.02", List.of(wave(enemy("monster.samurai", 3))))));
    }

    private static void assertDifficultyFails(StageWaveMasterFile waves, String part) {
        assertThatThrownBy(() -> StageWaveMasterValidator.validate(STAGES, waves, MONSTERS, DIFFICULTIES))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("[master/stage-waves.json]")
                .hasMessageContaining(part);
    }

    // 확인: 줄별 마리 수와 스테이지 배율 덮어쓰기가 함께 있는 정상 모양은 통과
    @Test
    void 난이도_칸_정상() {
        StageWaveMasterFile ok = new StageWaveMasterFile(1, List.of(
                new StageWavesEntry("stage.01.01", Map.of("2", new MultiplierOverride(null, 1.2, 1.2, null)),
                        List.of(wave(byDifficulty(Map.of("1", 4, "2", 5))), wave(enemy("monster.boss.warrior", 1)))),
                new StageWavesEntry("stage.01.02", List.of(wave(enemy("monster.samurai", 3))))));
        assertThatCode(() -> StageWaveMasterValidator.validate(STAGES, ok, MONSTERS, DIFFICULTIES))
                .doesNotThrowAnyException();
    }

    // W6: 값 0(난이도마다 몬스터 종류가 달라짐), 키 0, 숫자가 아닌 키, 없는 난이도
    @Test
    void 난이도별_마리_수() {
        assertDifficultyFails(waves(wave(byDifficulty(Map.of("1", 0)))), "countByDifficulty 값");
        assertDifficultyFails(waves(wave(byDifficulty(Map.of("0", 3)))), "0은 기본 값");
        assertDifficultyFails(waves(wave(byDifficulty(Map.of("hard", 3)))), "숫자");
        assertDifficultyFails(waves(wave(byDifficulty(Map.of("3", 3)))), "difficulties.json에 없는");
    }

    // W7: 이지(0) 덮어쓰기, 없는 난이도, 배율 0
    @Test
    void 스테이지_배율_덮어쓰기() {
        assertDifficultyFails(withOverride(Map.of("0", new MultiplierOverride(null, 1.2, null, null))), "0은 기본 값");
        assertDifficultyFails(withOverride(Map.of("3", new MultiplierOverride(null, 1.2, null, null))),
                "difficulties.json에 없는");
        assertDifficultyFails(withOverride(Map.of("2", new MultiplierOverride(null, 0.0, null, null))), "0보다");
    }

    // ---- 수식어: W8 affixes ----

    private static EnemyEntry withAffixes(List<String> affixes) {
        return new EnemyEntry("monster.samurai", 2, null, null, null, null, null, affixes);
    }

    // 확인: 수식어 여러 개(점이 든 키, 상한 개수), 빈 목록은 통과. 서버가 모르는 키도 모양만 맞으면 통과
    @Test
    void 수식어_정상() {
        StageWaveMasterFile ok = waves(wave(
                withAffixes(List.of("affix.fire", "affix.fast", "affix.vampiric.lv2", "affix.new.unknown")),
                withAffixes(List.of())));
        assertThatCode(() -> StageWaveMasterValidator.validate(STAGES, ok, MONSTERS)).doesNotThrowAnyException();
    }

    // W8: 접두어 없음, 대문자·밑줄, 빈 키, 한 줄 안 중복, 상한 초과
    @Test
    void 수식어_값() {
        assertFails(waves(wave(withAffixes(List.of("fire")))), MONSTERS, "stage-waves.json", "fire");
        assertFails(waves(wave(withAffixes(List.of("affix.Fire")))), MONSTERS, "stage-waves.json", "affix.Fire");
        assertFails(waves(wave(withAffixes(List.of("affix.fire_aura")))), MONSTERS, "stage-waves.json",
                "affix.fire_aura");
        assertFails(waves(wave(withAffixes(List.of("affix.")))), MONSTERS, "stage-waves.json", "수식어 키 모양");
        assertFails(waves(wave(withAffixes(List.of("affix.fire", "affix.fire")))), MONSTERS, "stage-waves.json",
                "겹칩니다");
        assertFails(waves(wave(withAffixes(List.of("affix.a", "affix.b", "affix.c", "affix.d", "affix.e")))),
                MONSTERS, "stage-waves.json", "4개까지");
    }

}
