package com.pw01.webserver.stageplay.service;

/**
 * 스테이지 플레이가 끝났다(결과 저장·포기). 커밋 뒤 PlayCheckpointCleaner가 그 플레이의 체크포인트를 지운다.
 * 만료(EXPIRED)는 발행하지 않는다: 체크포인트의 Redis 만료가 플레이 마감과 같아서 그때 함께 사라진다.
 */
public record StagePlayEndedEvent(String stagePlayId) {
}
