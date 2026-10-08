package com.pw01.webserver.run.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.run.dto.ResultSubmitRequest;
import com.pw01.webserver.run.dto.ResultSubmitResponse;
import com.pw01.webserver.run.service.RunResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결과 제출 API(R2). 인증 필요(WebConfig).
 * 판 시작(R1)은 스테이지 플레이 API(stageplay 패키지, POST /accounts/me/stage-plays)로 옮겼다.
 * 경로·이름(runId)은 결과 API(c)와 정한 뒤 바꾼다. 경로의 runId = 스테이지 플레이 ID.
 */
@RestController
@RequestMapping("/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunResultService runResultService;

    /** R2 결과 제출. 이미 저장된 판이면 처음 결과를 200으로 */
    @PostMapping("/{runId}/result")
    public ResultSubmitResponse submitResult(@LoginAccount Long accountId, @PathVariable String runId,
                                             @Valid @RequestBody ResultSubmitRequest request) {
        return runResultService.submit(accountId, runId, request);
    }

}
