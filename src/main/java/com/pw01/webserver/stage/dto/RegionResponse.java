package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.RegionDef;

import java.util.List;

/** ST1 지역 하나(stage-api.md). 스테이지는 order 순 */
public record RegionResponse(String regionId, String name, int order, List<StageResponse> stages) {

    public static RegionResponse from(RegionDef region) {
        return new RegionResponse(region.regionId(), region.name(), region.order(),
                region.stages().stream().map(StageResponse::from).toList());
    }

}
