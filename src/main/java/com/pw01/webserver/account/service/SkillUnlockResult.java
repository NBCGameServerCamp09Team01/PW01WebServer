package com.pw01.webserver.account.service;

import com.pw01.webserver.progression.requirement.RequirementProgress;

import java.util.List;

/**
 * 스킬 해금 판정·저장 결과(AccountService.unlockSkill). 오류 코드와 응답은 부른 쪽(SkillUnlockService)이 정한다.
 *
 * @param outcome      결과 종류
 * @param requirements REQUIREMENTS_NOT_MET일 때 그 스킬의 조건 전부(충족한 것 포함). 그 밖에는 빈 목록
 */
public record SkillUnlockResult(Outcome outcome, List<RequirementProgress> requirements) {

    public enum Outcome {
        /** 이번 요청으로 해금함 */
        SAVED,
        /** 이 요청(requestId)으로 이미 해금함. 아무것도 바꾸지 않음 */
        ALREADY_SAVED,
        /** 다른 요청으로 이미 해금한 스킬 */
        ALREADY_UNLOCKED,
        /** 조건 하나 이상 미충족 */
        REQUIREMENTS_NOT_MET
    }

    public static SkillUnlockResult of(Outcome outcome) {
        return new SkillUnlockResult(outcome, List.of());
    }

    public static SkillUnlockResult notMet(List<RequirementProgress> requirements) {
        return new SkillUnlockResult(Outcome.REQUIREMENTS_NOT_MET, List.copyOf(requirements));
    }

}
