package com.pw01.webserver.stageplay.entity;

/** 스테이지 플레이 상태. 한 방향: IN_PROGRESS → CLEARED·FAILED·EXPIRED */
public enum StagePlayStatus {
    /** 시작함. 계정당 하나 */
    IN_PROGRESS,
    /** 클리어 결과가 저장됨 */
    CLEARED,
    /** 실패 결과가 저장됨, 또는 포기함(endReason으로 구분) */
    FAILED,
    /** 마감이 지났는데 결과가 없음 */
    EXPIRED
}
