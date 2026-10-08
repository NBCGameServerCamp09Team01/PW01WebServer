package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.RegionDef;

import java.util.List;

/**
 * ST1 스테이지 목록(stage-api.md, GET /stages). 지역 → 스테이지 정의만 담고 계정 진행은 없다(ST2).
 *
 * @param totalCount 전체 스테이지 수
 * @param regions    지역 목록(order 순)
 */
public record StageListResponse(int totalCount, List<RegionResponse> regions) {

    public static StageListResponse from(List<RegionDef> regions, int totalCount) {
        return new StageListResponse(totalCount, regions.stream().map(RegionResponse::from).toList());
    }

}
