package com.pw01.webserver.guild.dto;

import com.pw01.webserver.stageplay.dto.StagePlayIds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * G5 가입 신청 요청(guild-api.md). guildId는 숫자 문자열(공통 규칙: ID는 문자열). 길드가 있는지는 서비스가 판정한다(404).
 */
public record GuildJoinRequestCreateRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotBlank(message = "guildId가 필요합니다.")
        @Pattern(regexp = "^[1-9][0-9]{0,17}$", message = "guildId는 숫자 문자열이어야 합니다.")
        String guildId
) {
}
