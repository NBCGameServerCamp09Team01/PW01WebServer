package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stage.master.DifficultyMasterFile;
import com.pw01.webserver.stage.master.MonsterMasterFile;
import com.pw01.webserver.stage.master.StageMasterFile;
import com.pw01.webserver.stage.master.StageWaveMasterFile;
import com.pw01.webserver.stage.repository.StageWaveFile;
import com.pw01.webserver.stage.repository.StageWaveSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 스테이지 정보: master/stages.json·stage-waves.json·monsters.json·difficulties.json을 기동 때 읽고 검사해 들고 있다(틀리면 기동 실패).
 * stage-waves.json은 StageWaveSource(prod = S3)에서, 나머지 셋은 리소스에서 읽는다.
 * "이 스테이지가 있나, 웨이브가 몇 개이고 무엇이 나오나, 지역·스테이지 순서는, 어떤 난이도가 있나"에 답한다.
 * 값은 정의 한 덩어리(StageContent)에 있고, 정의를 바꿀 때는 새 덩어리로 참조를 통째로 바꾼다(replace).
 * 읽는 쪽은 반쯤 바뀐 값을 보지 않는다. 한 요청 안에서 두 번 이상 읽는 곳은 content()를 한 번 받아 끝까지 쓴다
 * (예: 판 시작이 구성과 waveVersion을 같은 덩어리에서). 한 번만 읽는 곳은 아래 메서드를 그대로 쓴다.
 * 다른 기능(S2 판 시작·결과 검사, 랭킹)도 이 클래스를 부른다.
 */
@Component
public class StageCatalog {

    private static final Logger log = LoggerFactory.getLogger(StageCatalog.class);

    private static final String FILE_NAME = "stages.json";
    private static final String WAVES_FILE_NAME = "stage-waves.json";
    private static final String MONSTERS_FILE_NAME = "monsters.json";
    private static final String DIFFICULTIES_FILE_NAME = "difficulties.json";

    private final AtomicReference<StageContent> current;

    /**
     * 웨이브 정의는 waveSource(로컬·시험 = 리소스, prod = S3)에서, 나머지 셋은 리소스에서 읽는다.
     * S3를 못 읽거나 검사에 걸리면 예외 → 서버가 뜨지 않는다(배포 스크립트가 이전 컨테이너로 되돌림).
     */
    public StageCatalog(MasterDataLoader loader, StageWaveSource waveSource) {
        StageWaveFile waves = waveSource.read();
        this.current = new AtomicReference<>(StageContent.build(
                loader.load(FILE_NAME, StageMasterFile.class),
                loader.parse(WAVES_FILE_NAME, waves.content(), StageWaveMasterFile.class),
                loader.load(MONSTERS_FILE_NAME, MonsterMasterFile.class),
                loader.load(DIFFICULTIES_FILE_NAME, DifficultyMasterFile.class)));
        log.info("[Content] 정의 읽음 source={} waveVersion={}", waveSource.describe(), waveVersion());
    }

    /** 지금 정의 한 덩어리. 한 요청은 이것을 한 번만 부르고 그 덩어리로 끝까지 간다 */
    public StageContent content() {
        return current.get();
    }

    /**
     * 정의를 새 덩어리로 바꾼다. 지금 덩어리가 expected일 때만 바꾸고 true(그사이 다른 교체가 끼었으면 false).
     * 정의 다시 읽기만 부른다. 이미 시작한 판은 시작 때 저장한 구성을 쓰므로 영향이 없다
     */
    public boolean replace(StageContent expected, StageContent next) {
        return current.compareAndSet(expected, next);
    }

    public Optional<StageDef> find(String stageId) {
        return content().find(stageId);
    }

    /** 없으면 STAGE_NOT_FOUND */
    public StageDef get(String stageId) {
        return content().get(stageId);
    }

    public Optional<RegionDef> findRegion(String regionId) {
        return content().findRegion(regionId);
    }

    /** 없으면 REGION_NOT_FOUND(랭킹 지역 순위) */
    public RegionDef getRegion(String regionId) {
        return content().getRegion(regionId);
    }

    /** 결과 검사의 기준 웨이브 수. 없는 키면 STAGE_NOT_FOUND */
    public int waveCount(String stageId) {
        return get(stageId).waveCount();
    }

    /** 지역 order 순, 그 안의 스테이지 order 순 */
    public List<RegionDef> regions() {
        return content().regions();
    }

    /** 모든 스테이지를 regions()와 같은 순서로 */
    public List<StageDef> stages() {
        return content().stages();
    }

    public int totalCount() {
        return content().totalCount();
    }

    /** 몬스터 정의(monsters.json 순서). ST1 미리보기의 이름에 쓴다 */
    public List<MonsterDef> monsters() {
        return content().monsters();
    }

    /** 지금 서버의 웨이브 정보 버전(예: w2-m2-d1). 스테이지 플레이는 시작 때 이 값을 저장한다 */
    public String waveVersion() {
        return content().waveVersion();
    }

    /** 난이도 목록(번호 순, 0부터 빈틈없음). ST1·선택 화면 버튼 */
    public List<DifficultyDef> difficulties() {
        return content().difficulties();
    }

    /** 받는 난이도인가(스테이지 플레이 시작·랭킹 조회) */
    public boolean hasDifficulty(int difficulty) {
        return content().hasDifficulty(difficulty);
    }

    /** 랭킹 점수 배수(%). 없는 난이도면 IllegalArgumentException(시작 때 이미 확인한 값만 들어옴) */
    public int scoreMultiplierPercent(int difficulty) {
        return content().scoreMultiplierPercent(difficulty);
    }

}
