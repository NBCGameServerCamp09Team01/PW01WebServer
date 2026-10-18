package com.pw01.webserver.stage.service;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /actuator/info의 content 칸: 이 서버가 지금 쓰는 웨이브 정의(출처·waveVersion·읽은 시각).
 * 서버가 여러 대일 때 같은 정의를 쓰는지 서버마다 관리 포트로 한 번에 본다(루트 docs/flows/ops-health.md).
 */
@Component
public class StageContentInfoContributor implements InfoContributor {

    private final StageContentReloader reloader;

    public StageContentInfoContributor(StageContentReloader reloader) {
        this.reloader = reloader;
    }

    @Override
    public void contribute(Info.Builder builder) {
        StageContentReloader.Status status = reloader.status();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("source", status.source());
        content.put("waveVersion", status.waveVersion());
        content.put("loadedAt", status.loadedAt().toString());
        builder.withDetail("content", content);
    }

}
