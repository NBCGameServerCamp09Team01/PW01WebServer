package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.StageStatus;

import java.time.Instant;

/**
 * ST2 스테이지 하나의 내 진행(stage-api.md). firstClearedAt은 클리어 전에는 null로 나간다(칸은 늘 있음).
 * 나중에 메인화면 스냅샷에 진행을 넣을 때 이 모양을 그대로 옮긴다.
 *
 * @param stageId        스테이지 키
 * @param status         LOCKED · OPEN · CLEARED
 * @param firstClearedAt 서버가 처음 클리어 결과를 받아들인 시각. 클리어 전에는 null
 */
public record StageProgressResponse(String stageId, StageStatus status, Instant firstClearedAt) {
}
