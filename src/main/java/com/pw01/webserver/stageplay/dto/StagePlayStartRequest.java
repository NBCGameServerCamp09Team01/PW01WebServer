package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stageplay.entity.StagePlay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * P1 스테이지 플레이 시작 요청. requestId는 다시 보낼 때 같은 값(같은 값이면 처음 플레이를 돌려준다).
 * difficulty는 선택 칸: 없으면 0 = 난이도 조절 없음(기본). 지금은 0만 받는다(레벨 스케일링 등은 나중).
 */
public record StagePlayStartRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotBlank(message = "stageId가 필요합니다.")
        @Size(max = 64, message = "stageId는 64자 이하여야 합니다.")
        String stageId,

        @Min(value = 0, message = "difficulty는 지금 0(난이도 조절 없음)만 받습니다.")
        @Max(value = 0, message = "difficulty는 지금 0(난이도 조절 없음)만 받습니다.")
        Integer difficulty
) {

    public int difficultyOrDefault() {
        return difficulty == null ? StagePlay.NO_DIFFICULTY : difficulty;
    }

}
