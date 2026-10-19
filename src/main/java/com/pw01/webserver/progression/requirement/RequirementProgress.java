package com.pw01.webserver.progression.requirement;

/**
 * 조건 하나의 판정 결과(진행도). 스킬 해금 응답·진행도 조회가 그대로 내보낸다(루트 docs/contracts/skill-api.md "조건 미충족 응답").
 * 칸은 UE FWarriorSkillRequirementProgress와 맞춘다(current null = bHasValue false).
 *
 * @param type       조건 종류(RequirementTypes)
 * @param key        PREREQUISITE면 선행 스킬 태그, 나머지는 null
 * @param target     목표 값
 * @param current    지금 값. 기록이 없으면 null(클리어한 적 없는 BEST_CLEAR_TIME 등)
 * @param met        충족 여부
 * @param upperBound true면 "목표 이하"가 충족(BEST_CLEAR_TIME), false면 "목표 이상"
 */
public record RequirementProgress(String type, String key, double target, Double current, boolean met,
                                  boolean upperBound) {

    /** 지금 값 ≥ 목표면 충족. 값이 없으면 미충족 */
    public static RequirementProgress atLeast(String type, String key, double target, Double current) {
        return new RequirementProgress(type, key, target, current, current != null && current >= target, false);
    }

    /** 지금 값 ≤ 목표면 충족. 값이 없으면 미충족 */
    public static RequirementProgress atMost(String type, String key, double target, Double current) {
        return new RequirementProgress(type, key, target, current, current != null && current <= target, true);
    }

}
