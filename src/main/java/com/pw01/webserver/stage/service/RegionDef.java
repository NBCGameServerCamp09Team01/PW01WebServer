package com.pw01.webserver.stage.service;

import java.util.List;

/**
 * 지역 정의 하나(stages.json).
 *
 * @param regionId 지역 키
 * @param name     화면 이름
 * @param order    지역끼리의 화면 순서. 1부터
 * @param stages   그 지역의 스테이지(order 순)
 */
public record RegionDef(String regionId, String name, int order, List<StageDef> stages) {
}
