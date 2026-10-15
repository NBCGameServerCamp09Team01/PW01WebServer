package com.pw01.webserver.stage.dto;

import com.pw01.webserver.stage.service.StageDef;
import com.pw01.webserver.stage.service.WaveDef;

import java.util.List;
import java.util.stream.IntStream;

/**
 * ST1 스테이지 정의 하나(stage-api.md). 모두에게 같다. requires는 없으면 null로 나간다(칸은 늘 있음).
 * recommendedClearSeconds는 랭킹 점수의 기준 시간(v1.2)이다. waves는 선택 화면 미리보기(v1.3, 지금 서버 정의).
 * multipliers는 이 스테이지의 난이도 배율(v1.4, 덮어쓰기 반영), 미리보기 적 줄의 counts는 난이도별 마리 수(v1.4).
 */
public record StageResponse(String stageId, String name, int order, String requires, int waveCount,
                            int recommendedClearSeconds, List<StageMultiplierResponse> multipliers,
                            List<StageWavePreviewResponse> waves) {

    public static StageResponse from(StageDef stage) {
        List<StageMultiplierResponse> multipliers = IntStream.range(0, stage.multipliers().size())
                .mapToObj(difficulty -> StageMultiplierResponse.from(difficulty, stage.multipliers().get(difficulty)))
                .toList();
        List<WaveDef> base = stage.waves();
        List<StageWavePreviewResponse> waves = IntStream.range(0, base.size())
                .mapToObj(i -> StageWavePreviewResponse.from(base.get(i),
                        stage.wavesByDifficulty().stream().map(byDifficulty -> byDifficulty.get(i)).toList()))
                .toList();
        return new StageResponse(stage.stageId(), stage.name(), stage.order(), stage.requires(), stage.waveCount(),
                stage.recommendedClearSeconds(), multipliers, waves);
    }

}
