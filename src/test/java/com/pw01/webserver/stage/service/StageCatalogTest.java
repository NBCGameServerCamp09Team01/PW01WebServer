package com.pw01.webserver.stage.service;

import com.pw01.webserver.common.error.NotFoundException;
import com.pw01.webserver.common.master.MasterDataLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 master/stages.json으로 만든 스테이지 정보 단위 테스트(컨테이너 없음).
 * 배포할 파일이 검사를 통과하는지, 순서·requires·웨이브 수가 기대대로인지 본다.
 * 실패하면 stages.json 값(UE 웨이브 DataAsset과 맞춘 값인지)과 StageCatalog의 정렬을 의심한다.
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

    // 확인: 결과 검사의 기준 웨이브 수(UE 웨이브 DataAsset 값의 사본)
    @Test
    void 웨이브_수() {
        assertThat(catalog.waveCount("stage.01.01")).isEqualTo(5);
        assertThat(catalog.waveCount("stage.01.02")).isEqualTo(5);
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

}
