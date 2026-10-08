package com.pw01.webserver.stage.service;

/** 계정 기준 스테이지 상태. 서버가 계산하고 UE는 그리기만 한다(UE는 해금 규칙을 따로 갖지 않는다) */
public enum StageStatus {
    LOCKED,
    OPEN,
    CLEARED
}
