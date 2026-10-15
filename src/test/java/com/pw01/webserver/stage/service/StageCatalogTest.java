package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.error.NotFoundException;
import com.pw01.webserver.common.master.MasterDataLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 master/stages.json·stage-waves.json·monsters.json으로 만든 스테이지 정보 단위 테스트(컨테이너 없음).
 * 배포할 파일이 검사를 통과하는지, 순서·requires·웨이브 수·풀어 둔 웨이브 구성이 기대대로인지 본다.
 * 실패하면 마스터 파일 값(10/13 UE 웨이브 DataAsset에서 옮긴 값인지)과 StageCatalog의 정렬·풀기(toWaves)를 의심한다.
 */
class StageCatalogTest {

    private final StageCatalog catalog = new StageCatalog(new MasterDataLoader(
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()));

    // 확인: 지역 1 → 스테이지 1, 2 순서, 둘째의 requires는 첫째
    @Test
    void 지역과_스테이지_순서() {
        assertThat(catalog.regions()).extracting(RegionDef::regionId).containsExactly("region.01");
        assertThat(catalog.regions().getFirst().stages())
                .extracting(StageDef::stageId)
                .containsExactly("stage.01.01", "stage.01.02");
        assertThat(catalog.get("stage.01.01").requires()).isNull();
        assertThat(catalog.get("stage.01.02").requires()).isEqualTo("stage.01.01");
        assertThat(catalog.get("stage.01.02").regionId()).isEqualTo("region.01");
        assertThat(catalog.totalCount()).isEqualTo(2);
        assertThat(catalog.stages()).extracting(StageDef::stageId)
                .containsExactly("stage.01.01", "stage.01.02");
    }

    // 확인: 결과 검사·체크포인트 범위의 기준 웨이브 수(= stage-waves.json 웨이브 수)
    @Test
    void 웨이브_수() {
        assertThat(catalog.waveCount("stage.01.01")).isEqualTo(5);
        assertThat(catalog.waveCount("stage.01.02")).isEqualTo(5);
    }

    // 확인: 랭킹 점수 기준(권장 클리어 시간)과 지역 찾기. 틀리면 stages.json·toRegion을 의심
    @Test
    void 권장_시간과_지역() {
        assertThat(catalog.get("stage.01.01").recommendedClearSeconds()).isEqualTo(300);
        assertThat(catalog.getRegion("region.01").stages()).extracting(StageDef::stageId)
                .containsExactly("stage.01.01", "stage.01.02");
        assertThat(catalog.findRegion("region.99")).isEmpty();
        assertThatThrownBy(() -> catalog.getRegion("region.99"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo(StageErrors.REGION_NOT_FOUND));
    }

    // 확인: 없는 키는 STAGE_NOT_FOUND, find는 빈 값(null 키 포함), 키는 대소문자를 가린다
    @Test
    void 없는_키() {
        assertThatThrownBy(() -> catalog.get("stage.09.09"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo(StageErrors.STAGE_NOT_FOUND);
        assertThat(catalog.find("stage.09.09")).isEmpty();
        assertThat(catalog.find(null)).isEmpty();
        assertThat(catalog.find("Stage.01.01")).isEmpty();
    }

    // 확인(M24): 웨이브 정보 버전 = w{stage-waves 버전}-m{monsters 버전}, 몬스터 정의는 파일 순서
    @Test
    void 웨이브_버전과_몬스터() {
        assertThat(catalog.waveVersion()).isEqualTo("w2-m2-d1");
        assertThat(catalog.monsters()).extracting(MonsterDef::monsterId)
                .containsExactly("monster.samurai", "monster.archer", "monster.boss.warrior",
                        "monster.megaspikan.larvae", "monster.megaspikan.stalker", "monster.megaspikan.elite",
                        "monster.boss.rampage");
        assertThat(catalog.monsters()).extracting(MonsterDef::boss)
                .containsExactly(false, false, true, false, false, false, true);
    }

    // 확인(M24): 모든 스테이지의 웨이브 수 = waveCount, 번호는 1부터, 마지막만 보스 웨이브
    @Test
    void 웨이브_수와_번호() {
        for (StageDef stage : catalog.stages()) {
            assertThat(stage.waves()).hasSize(stage.waveCount());
            assertThat(stage.waves()).extracting(WaveDef::waveNumber).containsExactly(1, 2, 3, 4, 5);
            assertThat(stage.waves()).extracting(WaveDef::bossWave).containsExactly(false, false, false, false, true);
        }
    }

    // 확인(M24): 빈 칸은 기본값(쉬는 시간 0, 배율 1.0, 몬스터 정의 값), 덮어쓴 칸은 그 값(10/13 DA 값과 같음)
    @Test
    void 웨이브_구성_풀기() {
        List<WaveDef> waves = catalog.get("stage.01.01").waves();

        WaveDef first = waves.get(0);
        assertThat(first.restSeconds()).isZero();
        assertThat(first.goldChanceMultiplier()).isEqualTo(1.0);
        assertThat(first.goldAmountMultiplier()).isEqualTo(1.0);
        // 1웨이브 사무라이는 골드 3으로 덮어씀(몬스터 기본 2)
        assertThat(first.enemies()).containsExactly(new WaveEnemyDef("monster.samurai", 2, false, null, 3, 1.0));
        // 3웨이브는 덮어쓰기 없음 → 몬스터 기본(사무라이 2, 궁수 3)
        assertThat(waves.get(2).enemies()).containsExactly(
                new WaveEnemyDef("monster.samurai", 3, false, null, 2, 1.0),
                new WaveEnemyDef("monster.archer", 1, false, null, 3, 1.0));
        // 5웨이브(보스): 보스는 몬스터 정의의 boss true, 나머지는 골드 0으로 덮어씀
        assertThat(waves.get(4).enemies()).containsExactly(
                new WaveEnemyDef("monster.boss.warrior", 1, true, null, 0, 1.0),
                new WaveEnemyDef("monster.samurai", 2, false, null, 0, 1.0),
                new WaveEnemyDef("monster.archer", 1, false, null, 0, 1.0));

        // stage.01.02는 비인간 몬스터(메가스피칸)만: 유충 5 / 유충 5 + 잠복 2 / 유충 4 + 잠복 3 /
        // 유충 5 + 잠복 2 + 정예 2 / 램페이지 + 유충 4 + 정예 1
        List<WaveDef> second = catalog.get("stage.01.02").waves();
        assertThat(second)
                .extracting(wave -> wave.enemies().stream().map(WaveEnemyDef::count).toList())
                .containsExactly(List.of(5), List.of(5, 2), List.of(4, 3), List.of(5, 2, 2), List.of(1, 4, 1));
        // 1웨이브 유충은 골드 2로 덮어씀(몬스터 기본 1), 보스 웨이브 첫 줄은 램페이지(보스)
        assertThat(second.get(0).enemies())
                .containsExactly(new WaveEnemyDef("monster.megaspikan.larvae", 5, false, null, 2, 1.0));
        assertThat(second.get(4).enemies().get(0))
                .isEqualTo(new WaveEnemyDef("monster.boss.rampage", 1, true, null, 0, 1.0));
    }

    // 확인(M9): 실제 difficulties.json은 이지·노말·하드, 0은 배율 1.0, 받는 난이도는 0~2, 점수 배수는 모두 100
    @Test
    void 난이도_목록() {
        assertThat(catalog.difficulties()).extracting(DifficultyDef::name).containsExactly("이지", "노말", "하드");
        assertThat(catalog.difficulties().getFirst().multipliers()).isEqualTo(DifficultyMultipliers.NONE);
        assertThat(catalog.hasDifficulty(2)).isTrue();
        assertThat(catalog.hasDifficulty(3)).isFalse();
        assertThat(catalog.hasDifficulty(-1)).isFalse();
        assertThat(catalog.scoreMultiplierPercent(1)).isEqualTo(100);
        assertThatThrownBy(() -> catalog.scoreMultiplierPercent(3)).isInstanceOf(IllegalArgumentException.class);
    }

    // 확인(M9): 난이도별 구성. 난이도 0은 waves와 같고, 노말·하드는 같은 줄(몬스터 종류)·같은 수(수 배율 1.0)에
    // 체력·공격력 배율만 다르다. 실패하면 StageCatalog.toStage·toEnemy, 실제 difficulties.json 값을 의심한다
    @Test
    void 난이도별_구성() {
        StageDef stage = catalog.get("stage.01.01");
        assertThat(stage.wavesFor(0)).isEqualTo(stage.waves());
        assertThat(stage.wavesByDifficulty()).hasSize(3);
        assertThat(stage.multipliers()).containsExactly(DifficultyMultipliers.NONE,
                new DifficultyMultipliers(1.0, 1.2, 1.2, 1.0), new DifficultyMultipliers(1.0, 1.5, 1.3, 1.0));

        WaveEnemyDef hardFirst = stage.wavesFor(2).getFirst().enemies().getFirst();
        assertThat(hardFirst).isEqualTo(new WaveEnemyDef("monster.samurai", 2, false, null, 3, 1.0, 1.5, 1.3));
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            assertThat(stage.wavesFor(difficulty))
                    .extracting(wave -> wave.enemies().stream().map(WaveEnemyDef::monsterId).toList())
                    .isEqualTo(stage.waves().stream()
                            .map(wave -> wave.enemies().stream().map(WaveEnemyDef::monsterId).toList()).toList());
        }
    }

}
