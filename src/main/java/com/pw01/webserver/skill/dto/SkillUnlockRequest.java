package com.pw01.webserver.skill.dto;

import com.pw01.webserver.stageplay.dto.StagePlayIds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 스킬 해금 요청(루트 docs/contracts/skill-api.md K1). 형식만 여기서 본다.
 * 없는 스킬은 SkillService가(SKILL_UNKNOWN), 이미 해금·조건은 계정 기능이 판정한다.
 */
public record SkillUnlockRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotBlank(message = "skillTag가 필요합니다.")
        @Size(max = 100, message = "skillTag는 100자 이하여야 합니다.")
        String skillTag
) {
}
