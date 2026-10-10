package com.pw01.webserver.stageplay.entity;

/** 스테이지 플레이가 끝난 이유. 진행 중이면 없다(null) */
public enum StagePlayEndReason {
    /** 결과 제출(CLEARED 또는 FAILED) */
    RESULT,
    /** 포기(상태는 FAILED). 결과·보상이 없다 */
    ABANDONED,
    /** 마감 지남(상태는 EXPIRED) */
    EXPIRED
}
