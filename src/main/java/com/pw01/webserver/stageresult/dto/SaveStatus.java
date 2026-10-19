package com.pw01.webserver.stageresult.dto;

/** 결과 제출 응답의 저장 상태. 둘 다 200이고, 게임은 어느 쪽이든 "저장됨"으로 보고 다음으로 간다 */
public enum SaveStatus {
    /** 이번 요청으로 새로 저장함(보상·진행 반영도 이번에) */
    SAVED,
    /** 이 플레이의 결과가 이미 있어 처음 결과를 돌려줌(재전송·동시 제출). 보상은 다시 주지 않음 */
    ALREADY_SAVED
}
