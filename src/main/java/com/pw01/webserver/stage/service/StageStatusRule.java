package com.pw01.webserver.stage.service;

import java.util.Set;

/**
 * 해금 규칙(계산만, DB 없음). 첫 스테이지는 계정을 만들면 바로 열리고(진행 행 없이 계산),
 * 그 뒤로는 requires 스테이지를 클리어하면 열린다.
 */
final class StageStatusRule {

    private StageStatusRule() {
    }

    /**
     * @param stage      판단할 스테이지
     * @param clearedIds 계정이 클리어한 스테이지 키
     */
    static StageStatus of(StageDef stage, Set<String> clearedIds) {
        if (clearedIds.contains(stage.stageId())) {
            return StageStatus.CLEARED;
        }
        return isUnlocked(stage, clearedIds) ? StageStatus.OPEN : StageStatus.LOCKED;
    }

    /** 클리어한 스테이지도 열린 것으로 본다(다시 할 수 있다) */
    static boolean isUnlocked(StageDef stage, Set<String> clearedIds) {
        return stage.requires() == null || clearedIds.contains(stage.requires());
    }

}
