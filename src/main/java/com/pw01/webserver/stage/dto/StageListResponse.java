package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.DifficultyDef;
import com.pw01.webserver.stage.service.MonsterDef;
import com.pw01.webserver.stage.service.RegionDef;

import java.util.List;

/**
 * ST1 스테이지 목록(stage-api.md, GET /stages). 지역 → 스테이지 정의만 담고 계정 진행은 없다(ST2).
 *
 * @param totalCount   전체 스테이지 수
 * @param waveVersion  지금 서버의 웨이브 정보 버전(예: w2-m2-d1, v1.3·v1.4)
 * @param difficulties 난이도 목록(번호 순, v1.4)
 * @param monsters     몬스터 정의(미리보기 이름, v1.3)
 * @param regions      지역 목록(order 순)
 */
public record StageListResponse(int totalCount, String waveVersion, List<DifficultyResponse> difficulties,
                                List<MonsterResponse> monsters, List<RegionResponse> regions) {

    public static StageListResponse from(List<RegionDef> regions, int totalCount, String waveVersion,
                                         List<DifficultyDef> difficulties, List<MonsterDef> monsters) {
        return new StageListResponse(totalCount, waveVersion,
                difficulties.stream().map(DifficultyResponse::from).toList(),
                monsters.stream().map(MonsterResponse::from).toList(),
                regions.stream().map(RegionResponse::from).toList());
    }

}
