package com.pw01.webserver.stage.controller;

import com.pw01.webserver.realtime.service.RealtimeNotifier;
import com.pw01.webserver.common.server.ServerId;
import com.pw01.webserver.stage.service.StageContentReloader;
import com.pw01.webserver.stage.service.StageContentReloader.Outcome;
import com.pw01.webserver.stage.service.StageContentReloader.Result;
import com.pw01.webserver.stage.service.StageContentReloader.Status;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리 포트 웨이브 정의 다시 읽기(판 12, 루트 docs/flows/ops-health.md "웨이브 정의 다시 읽기").
 * GET /actuator/stagecontent = 지금 상태, POST = 다시 읽기. 운영자가 Session Manager로 서버 하나에서 부른다(게임은 부르지 않음).
 * prod는 관리 포트(8081)에만 열리고 밖에서 막힌다(보안 그룹). 본문 없는 "다시 읽기"뿐이라 누가 불러도 원본(S3)을 다시 볼 뿐이다.
 * 바뀌었으면(CHANGED) 다른 서버에 알린다. 응답은 Actuator 모양(감싸지 않음)이고 결과와 상관없이 200, 결과는 result 칸.
 */
@Component
@Endpoint(id = "stagecontent")
public class StageContentEndpoint {

    private final StageContentReloader reloader;
    private final RealtimeNotifier notifier;
    private final ServerId serverId;

    public StageContentEndpoint(StageContentReloader reloader, RealtimeNotifier notifier, ServerId serverId) {
        this.reloader = reloader;
        this.notifier = notifier;
        this.serverId = serverId;
    }

    @ReadOperation
    public Map<String, Object> status() {
        Status status = reloader.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", status.source());
        body.put("waveVersion", status.waveVersion());
        body.put("loadedAt", status.loadedAt().toString());
        body.put("lastCheckAt", status.lastCheckAt() == null ? null : status.lastCheckAt().toString());
        body.put("lastRejected", status.lastRejected() == null ? null
                : Map.of("at", status.lastRejected().at().toString(), "reason", status.lastRejected().reason()));
        body.put("serverId", serverId.value());
        return body;
    }

    @WriteOperation
    public Map<String, Object> reload() {
        Outcome outcome = reloader.reload("signal");
        if (outcome.result() == Result.CHANGED) {
            notifier.notifyContentChanged();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", outcome.result().name());
        body.put("before", outcome.before());
        body.put("waveVersion", outcome.waveVersion());
        if (outcome.reason() != null) {
            body.put("reason", outcome.reason());
        }
        body.put("serverId", serverId.value());
        return body;
    }

}
