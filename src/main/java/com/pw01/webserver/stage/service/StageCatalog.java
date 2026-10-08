package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stage.master.StageMasterFile;
import com.pw01.webserver.stage.master.StageMasterFile.RegionEntry;
import com.pw01.webserver.stage.master.StageMasterFile.StageEntry;
import com.pw01.webserver.stage.master.StageMasterValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 스테이지 정보: master/stages.json을 기동 때 읽고 검사해 들고 있다(틀리면 기동 실패).
 * "이 스테이지가 있나, 웨이브가 몇 개인가, 지역·스테이지 순서는"에 답한다. 값은 기동 뒤 바뀌지 않는다.
 * 다른 기능(S2 판 시작·결과 검사)도 이 클래스를 부른다.
 */
@Component
public class StageCatalog {

    private static final String FILE_NAME = "stages.json";

    /** 지역 order 순, 그 안의 스테이지 order 순 */
    private final List<RegionDef> regions;
    /** stageId → 정의. 순서는 regions와 같다 */
    private final Map<String, StageDef> stages;

    public StageCatalog(MasterDataLoader loader) {
        StageMasterFile file = loader.load(FILE_NAME, StageMasterFile.class);
        StageMasterValidator.validate(file);

        Map<String, StageDef> byId = new LinkedHashMap<>();
        this.regions = file.regions().stream()
                .sorted(Comparator.comparingInt(RegionEntry::order))
                .map(region -> toRegion(region, byId))
                .toList();
        this.stages = Collections.unmodifiableMap(byId);
    }

    private static RegionDef toRegion(RegionEntry region, Map<String, StageDef> byId) {
        List<StageDef> defs = region.stages().stream()
                .sorted(Comparator.comparingInt(StageEntry::order))
                .map(stage -> new StageDef(stage.stageId(), region.regionId(), stage.name(), stage.order(),
                        stage.requires(), stage.waveCount()))
                .toList();
        defs.forEach(def -> byId.put(def.stageId(), def));
        return new RegionDef(region.regionId(), region.name(), region.order(), defs);
    }

    public Optional<StageDef> find(String stageId) {
        return Optional.ofNullable(stageId).map(stages::get);
    }

    /** 없으면 STAGE_NOT_FOUND */
    public StageDef get(String stageId) {
        return find(stageId).orElseThrow(StageErrors::notFound);
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

}
