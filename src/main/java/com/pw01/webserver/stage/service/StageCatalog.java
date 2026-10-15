package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stage.master.DifficultyMasterFile;
import com.pw01.webserver.stage.master.DifficultyMasterFile.DifficultyEntry;
import com.pw01.webserver.stage.master.DifficultyMasterValidator;
import com.pw01.webserver.stage.master.MonsterMasterFile;
import com.pw01.webserver.stage.master.MonsterMasterFile.MonsterEntry;
import com.pw01.webserver.stage.master.StageMasterFile;
import com.pw01.webserver.stage.master.StageMasterFile.RegionEntry;
import com.pw01.webserver.stage.master.StageMasterFile.StageEntry;
import com.pw01.webserver.stage.master.StageMasterValidator;
import com.pw01.webserver.stage.master.StageWaveMasterFile;
import com.pw01.webserver.stage.master.StageWaveMasterFile.EnemyEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.MultiplierOverride;
import com.pw01.webserver.stage.master.StageWaveMasterFile.StageWavesEntry;
import com.pw01.webserver.stage.master.StageWaveMasterFile.WaveEntry;
import com.pw01.webserver.stage.master.StageWaveMasterValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 스테이지 정보: master/stages.json·stage-waves.json·monsters.json·difficulties.json을 기동 때 읽고 검사해 들고 있다(틀리면 기동 실패).
 * "이 스테이지가 있나, 웨이브가 몇 개이고 무엇이 나오나, 지역·스테이지 순서는, 어떤 난이도가 있나"에 답한다. 값은 기동 뒤 바뀌지 않는다.
 * 웨이브 구성은 몬스터 정의 값과 기본값을 풀고, 난이도마다 마리 수·배율을 반영해(M9, StageDifficultyRule)
 * StageDef.wavesByDifficulty에 미리 만들어 둔다(stage-api.md "서버 정의 파일"·"난이도").
 * 다른 기능(S2 판 시작·결과 검사, 랭킹)도 이 클래스를 부른다.
 */
@Component
public class StageCatalog {

    private static final String FILE_NAME = "stages.json";
    private static final String WAVES_FILE_NAME = "stage-waves.json";
    private static final String MONSTERS_FILE_NAME = "monsters.json";
    private static final String DIFFICULTIES_FILE_NAME = "difficulties.json";

    /** 지역 order 순, 그 안의 스테이지 order 순 */
    private final List<RegionDef> regions;
    /** stageId → 정의. 순서는 regions와 같다 */
    private final Map<String, StageDef> stages;
    /** monsters.json 순서 */
    private final List<MonsterDef> monsters;
    /** 난이도 번호 순(0부터 빈틈없음) */
    private final List<DifficultyDef> difficulties;
    /** 웨이브 정보 버전 "w{stage-waves 버전}-m{monsters 버전}-d{difficulties 버전}". 스테이지 플레이가 시작 때 저장한다 */
    private final String waveVersion;

    public StageCatalog(MasterDataLoader loader) {
        StageMasterFile file = loader.load(FILE_NAME, StageMasterFile.class);
        StageMasterValidator.validate(file);
        StageWaveMasterFile wavesFile = loader.load(WAVES_FILE_NAME, StageWaveMasterFile.class);
        MonsterMasterFile monstersFile = loader.load(MONSTERS_FILE_NAME, MonsterMasterFile.class);
        DifficultyMasterFile difficultiesFile = loader.load(DIFFICULTIES_FILE_NAME, DifficultyMasterFile.class);
        StageWaveMasterValidator.validate(file, wavesFile, monstersFile,
                DifficultyMasterValidator.validate(difficultiesFile));

        this.monsters = monstersFile.monsters().stream().map(StageCatalog::toMonster).toList();
        this.difficulties = difficultiesFile.difficulties().stream().map(StageCatalog::toDifficulty).toList();
        this.waveVersion = "w" + wavesFile.version() + "-m" + monstersFile.version()
                + "-d" + difficultiesFile.version();
        Map<String, MonsterDef> monsterById = monsters.stream()
                .collect(Collectors.toMap(MonsterDef::monsterId, Function.identity()));
        Map<String, StageWavesEntry> wavesByStage = wavesFile.stages().stream()
                .collect(Collectors.toMap(StageWavesEntry::stageId, Function.identity()));

        Map<String, StageDef> byId = new LinkedHashMap<>();
        this.regions = file.regions().stream()
                .sorted(Comparator.comparingInt(RegionEntry::order))
                .map(region -> toRegion(region, wavesByStage, monsterById, difficulties, byId))
                .toList();
        this.stages = Collections.unmodifiableMap(byId);
    }

    private static RegionDef toRegion(RegionEntry region, Map<String, StageWavesEntry> wavesByStage,
                                      Map<String, MonsterDef> monsterById, List<DifficultyDef> difficulties,
                                      Map<String, StageDef> byId) {
        List<StageDef> defs = region.stages().stream()
                .sorted(Comparator.comparingInt(StageEntry::order))
                .map(stage -> toStage(stage, region.regionId(), wavesByStage.get(stage.stageId()), monsterById,
                        difficulties))
                .toList();
        defs.forEach(def -> byId.put(def.stageId(), def));
        return new RegionDef(region.regionId(), region.name(), region.order(), defs);
    }

    /** 난이도마다 배율(덮어쓰기 반영)과 웨이브 구성을 만든다. 0번이 StageDef.waves */
    private static StageDef toStage(StageEntry stage, String regionId, StageWavesEntry wavesEntry,
                                    Map<String, MonsterDef> monsterById, List<DifficultyDef> difficulties) {
        List<DifficultyMultipliers> multipliers = difficulties.stream()
                .map(difficulty -> StageDifficultyRule.multipliers(difficulty.multipliers(),
                        override(wavesEntry, difficulty.difficulty())))
                .toList();
        List<List<WaveDef>> wavesByDifficulty = difficulties.stream()
                .map(difficulty -> toWaves(wavesEntry, monsterById, difficulty.difficulty(),
                        multipliers.get(difficulty.difficulty())))
                .toList();
        return new StageDef(stage.stageId(), regionId, stage.name(), stage.order(), stage.requires(),
                stage.waveCount(), stage.recommendedClearSeconds(), wavesByDifficulty.getFirst(), wavesByDifficulty,
                multipliers);
    }

    private static MultiplierOverride override(StageWavesEntry entry, int difficulty) {
        return entry.difficultyMultipliers() == null ? null
                : entry.difficultyMultipliers().get(String.valueOf(difficulty));
    }

    private static DifficultyDef toDifficulty(DifficultyEntry entry) {
        return new DifficultyDef(entry.difficulty(), entry.name(),
                new DifficultyMultipliers(entry.enemyCountMultiplier(), entry.enemyHealthMultiplier(),
                        entry.enemyDamageMultiplier(), entry.goldAmountMultiplier()),
                entry.scoreMultiplierPercent());
    }

    private static MonsterDef toMonster(MonsterEntry entry) {
        return new MonsterDef(entry.monsterId(), entry.name(), entry.boss(), entry.goldReward(), entry.goldDropChance());
    }

    /**
     * 웨이브 파일의 빈 칸을 기본값(쉬는 시간 0, 배율 1.0)과 몬스터 정의 값으로 채우고 그 난이도를 반영한다.
     * 순서가 웨이브 번호(1부터). 웨이브 골드 양 배율에 난이도 골드 배율을 곱하고, 적 줄마다 마리 수·체력·공격력 배율을 정한다
     */
    private static List<WaveDef> toWaves(StageWavesEntry entry, Map<String, MonsterDef> monsterById, int difficulty,
                                         DifficultyMultipliers multipliers) {
        List<WaveEntry> waves = entry.waves();
        return IntStream.range(0, waves.size())
                .mapToObj(i -> {
                    WaveEntry wave = waves.get(i);
                    List<WaveEnemyDef> enemies = wave.enemies().stream()
                            .map(enemy -> toEnemy(enemy, monsterById.get(enemy.monsterId()), difficulty, multipliers))
                            .toList();
                    return new WaveDef(i + 1,
                            Objects.requireNonNullElse(wave.restSeconds(), 0.0),
                            Objects.requireNonNullElse(wave.bossWave(), false),
                            Objects.requireNonNullElse(wave.goldChanceMultiplier(), 1.0),
                            Objects.requireNonNullElse(wave.goldAmountMultiplier(), 1.0)
                                    * multipliers.goldAmountMultiplier(),
                            enemies);
                })
                .toList();
    }

    private static WaveEnemyDef toEnemy(EnemyEntry enemy, MonsterDef monster, int difficulty,
                                        DifficultyMultipliers multipliers) {
        boolean boss = Objects.requireNonNullElse(enemy.boss(), monster.boss());
        return new WaveEnemyDef(enemy.monsterId(),
                StageDifficultyRule.count(enemy.count(), boss, enemy.countByDifficulty(), difficulty,
                        multipliers.enemyCountMultiplier()),
                boss,
                enemy.spawnGroup(),
                Objects.requireNonNullElse(enemy.goldReward(), monster.goldReward()),
                Objects.requireNonNullElse(enemy.goldDropChance(), monster.goldDropChance()),
                multipliers.enemyHealthMultiplier(),
                multipliers.enemyDamageMultiplier());
    }

    public Optional<StageDef> find(String stageId) {
        return Optional.ofNullable(stageId).map(stages::get);
    }

    /** 없으면 STAGE_NOT_FOUND */
    public StageDef get(String stageId) {
        return find(stageId).orElseThrow(StageErrors::notFound);
    }

    public Optional<RegionDef> findRegion(String regionId) {
        return regions.stream().filter(region -> region.regionId().equals(regionId)).findFirst();
    }

    /** 없으면 REGION_NOT_FOUND(랭킹 지역 순위) */
    public RegionDef getRegion(String regionId) {
        return findRegion(regionId).orElseThrow(StageErrors::regionNotFound);
    }

    /** 결과 검사의 기준 웨이브 수. 없는 키면 STAGE_NOT_FOUND */
    public int waveCount(String stageId) {
        return get(stageId).waveCount();
    }

    /** 지역 order 순, 그 안의 스테이지 order 순 */
    public List<RegionDef> regions() {
        return regions;
    }

    /** 모든 스테이지를 regions()와 같은 순서로 */
    public List<StageDef> stages() {
        return List.copyOf(stages.values());
    }

    public int totalCount() {
        return stages.size();
    }

    /** 몬스터 정의(monsters.json 순서). ST1 미리보기의 이름에 쓴다 */
    public List<MonsterDef> monsters() {
        return monsters;
    }

    /** 지금 서버의 웨이브 정보 버전(예: w2-m2-d1). 스테이지 플레이는 시작 때 이 값을 저장한다 */
    public String waveVersion() {
        return waveVersion;
    }

    /** 난이도 목록(번호 순, 0부터 빈틈없음). ST1·선택 화면 버튼 */
    public List<DifficultyDef> difficulties() {
        return difficulties;
    }

    /** 받는 난이도인가(스테이지 플레이 시작·랭킹 조회) */
    public boolean hasDifficulty(int difficulty) {
        return difficulty >= 0 && difficulty < difficulties.size();
    }

    /** 랭킹 점수 배수(%). 없는 난이도면 IllegalArgumentException(시작 때 이미 확인한 값만 들어옴) */
    public int scoreMultiplierPercent(int difficulty) {
        if (!hasDifficulty(difficulty)) {
            throw new IllegalArgumentException("없는 난이도입니다: " + difficulty);
        }
        return difficulties.get(difficulty).scoreMultiplierPercent();
    }

}
