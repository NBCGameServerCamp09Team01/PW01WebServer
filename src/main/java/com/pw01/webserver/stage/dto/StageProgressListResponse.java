package com.pw01.webserver.stage.dto;

import java.util.List;

/**
 * ST2 내 스테이지 진행(stage-api.md, GET /accounts/me/stages). 스테이지 순서는 ST1과 같다(지역 order → 스테이지 order).
 *
 * @param clearedCount 클리어한 스테이지 수(마스터 데이터에 있는 것만)
 * @param totalCount   전체 스테이지 수
 * @param stages       스테이지마다 내 상태
 */
public record StageProgressListResponse(int clearedCount, int totalCount, List<StageProgressResponse> stages) {
}
