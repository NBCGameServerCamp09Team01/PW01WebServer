package com.pw01.webserver.guild.dto;

import com.pw01.webserver.stageplay.dto.StagePlayIds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 본문이 requestId 하나뿐인 길드 쓰기 요청(G8 수락·G9 거절·G11 탈퇴·G12 해체). 대상은 경로에 있다.
 * 멱등 키의 본문 해시에는 이 요청이 아니라 동작·대상을 함께 담은 GuildActionKey를 쓴다(같은 requestId를 다른 대상에 쓰면 409).
 */
public record GuildActionRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId
) {
}
