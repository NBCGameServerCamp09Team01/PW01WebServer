package com.pw01.webserver.stageresult.dto;

import com.pw01.webserver.stageplay.dto.StagePlayIds;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 결과 제출 요청. 칸은 UE FWarriorStageResult를 원본으로 한다(TD-15).
 * 형식만 여기서 본다. 플레이와 맞는지·웨이브·시간 같은 판정은 StageResultService가 한다(RESULT_MISMATCH·RESULT_INVALID).
 * reachedWave는 음수도 형식으로는 받고 판정에서 거절한다(웨이브 규칙 하나로 모음, StageWaveRule).
 * difficulty는 0 = 난이도 조절 없음(기본). 지금은 0만 받는다.
 */
public record ResultSubmitRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotBlank(message = "stageId가 필요합니다.")
        @Size(max = 64, message = "stageId는 64자 이하여야 합니다.")
        String stageId,

        @Min(value = 0, message = "difficulty는 지금 0(난이도 조절 없음)만 받습니다.")
        @Max(value = 0, message = "difficulty는 지금 0(난이도 조절 없음)만 받습니다.")
        Integer difficulty,

        @NotNull(message = "cleared가 필요합니다.")
        Boolean cleared,

        @NotNull(message = "reachedWave가 필요합니다.")
        Integer reachedWave,

        @NotNull(message = "totalWaveCount가 필요합니다.")
        @PositiveOrZero(message = "totalWaveCount는 0 이상이어야 합니다.")
        Integer totalWaveCount,

        @NotNull(message = "playTimeSeconds가 필요합니다.")
        Double playTimeSeconds,

        @NotNull(message = "earnedGold가 필요합니다.")
        @PositiveOrZero(message = "earnedGold는 0 이상이어야 합니다.")
        Integer earnedGold,

        @NotNull(message = "killCount가 필요합니다.")
        @PositiveOrZero(message = "killCount는 0 이상이어야 합니다.")
        Integer killCount
) {

    public int difficultyOrZero() {
        return difficulty == null ? 0 : difficulty;
    }

}
