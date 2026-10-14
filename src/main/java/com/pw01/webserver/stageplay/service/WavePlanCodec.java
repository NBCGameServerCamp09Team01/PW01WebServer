package com.pw01.webserver.stageplay.service;

import com.pw01.webserver.stage.service.WaveDef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 스테이지 플레이의 웨이브 구성(stage_play.wave_plan) JSON 쓰기·읽기. 칸은 WaveDef와 같다(= 응답 waves).
 * 서버가 시작 때 쓴 값만 읽으므로 읽지 못하면 서버 버그다(예외 그대로 → 500).
 */
@Component
@RequiredArgsConstructor
public class WavePlanCodec {

    private static final TypeReference<List<WaveDef>> WAVES = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    public String write(List<WaveDef> waves) {
        return jsonMapper.writeValueAsString(waves);
    }

    public List<WaveDef> read(String wavePlan) {
        return jsonMapper.readValue(wavePlan, WAVES);
    }

}
