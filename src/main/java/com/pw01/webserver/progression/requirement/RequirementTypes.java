package com.pw01.webserver.progression.requirement;

/**
 * 조건 종류 이름(루트 docs/contracts/skill-api.md "조건 종류"). UE EWarriorSkillRequirementType과 1:1에 PREREQUISITE·STAT_POINTS를 더했다.
 * PREREQUISITE·STAT_POINTS는 마스터의 선행 목록·비용에서 만들고, 나머지는 마스터 조건 목록에 쓴다(판정기 표에 있는 것만).
 */
public final class RequirementTypes {

    public static final String PREREQUISITE = "PREREQUISITE";
    public static final String STAT_POINTS = "STAT_POINTS";
    public static final String ACCOUNT_LEVEL = "ACCOUNT_LEVEL";
    public static final String STAGES_CLEARED = "STAGES_CLEARED";
    public static final String TOTAL_KILLS = "TOTAL_KILLS";
    public static final String PLAY_TIME_SECONDS = "PLAY_TIME_SECONDS";
    public static final String MAX_WAVE_REACHED = "MAX_WAVE_REACHED";
    public static final String BEST_CLEAR_TIME = "BEST_CLEAR_TIME";

    private RequirementTypes() {
    }

}
