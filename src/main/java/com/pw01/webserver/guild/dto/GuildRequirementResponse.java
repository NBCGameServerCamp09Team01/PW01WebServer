package com.pw01.webserver.guild.dto;

/**
 * 길드 만들기 조건 하나. G4 create.requirements[]와 409 GUILD_CREATE_REQUIREMENTS_NOT_MET의 requirements가 같은 모양이다.
 *
 * @param type    지금은 STAGE_CLEARED 하나
 * @param stageId 클리어해야 하는 스테이지
 * @param met     충족 여부
 */
public record GuildRequirementResponse(String type, String stageId, boolean met) {

    public static final String STAGE_CLEARED = "STAGE_CLEARED";

    public static GuildRequirementResponse stageCleared(String stageId, boolean met) {
        return new GuildRequirementResponse(STAGE_CLEARED, stageId, met);
    }

}
