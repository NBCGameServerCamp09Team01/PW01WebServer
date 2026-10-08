package com.pw01.webserver.run.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * R1 판 시작 요청(result-api.md). requestId는 상태를 바꾸는 요청의 공통 칸(README "상태를 바꾸는 요청과 재전송").
 * difficulty는 선택 칸: 없으면 0, 지금은 0만 받는다(d와 맞춤, 안건 3-4).
 */
public record RunStartRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = RunRequestRules.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotBlank(message = "stageId가 필요합니다.")
        @Size(max = 64, message = "stageId는 64자 이하여야 합니다.")
        String stageId,

        @Min(value = 0, message = "difficulty는 지금 0만 받습니다.")
        @Max(value = 0, message = "difficulty는 지금 0만 받습니다.")
        Integer difficulty
) {

    public int difficultyOrZero() {
        return difficulty == null ? 0 : difficulty;
    }

}
