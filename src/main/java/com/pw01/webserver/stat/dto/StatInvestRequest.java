package com.pw01.webserver.stat.dto;

import com.pw01.webserver.stageplay.dto.StagePlayIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 묶음 투자 요청(루트 docs/contracts/stat-api.md T1). 형식만 여기서 본다.
 * 0·같은 스탯 두 번·묶음 상한(설정 값)은 StatInvestService가, 포인트·최대치·0 아래는 계정 기능이 판정한다.
 */
public record StatInvestRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotEmpty(message = "investments는 1개 이상이어야 합니다.")
        @Valid
        List<Investment> investments
) {

    /** 스탯 하나. points 양수 = 올림, 음수 = 내림 */
    public record Investment(

            @NotBlank(message = "statTag가 필요합니다.")
            @Size(max = 100, message = "statTag는 100자 이하여야 합니다.")
            String statTag,

            @NotNull(message = "points가 필요합니다.")
            Integer points
    ) {
    }

}
