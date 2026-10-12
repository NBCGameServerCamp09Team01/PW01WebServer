package com.pw01.webserver.ranking.service;

/**
 * 보드 종류(ranking-api.md "보드와 순서"). 응답 board.kind에 이름 그대로 나간다.
 * STAGE·REGION은 대상(스테이지·지역 키) × 난이도 × 기간, TOTAL은 모든 지역 × 난이도 × 기간(대상 없음),
 * PROGRESS는 전체 하나(난이도 없음, 전체 기간만).
 */
public enum RankingBoardKind {
    STAGE,
    REGION,
    TOTAL,
    PROGRESS
}
