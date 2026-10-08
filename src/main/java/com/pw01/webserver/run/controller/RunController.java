package com.pw01.webserver.run.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.run.dto.ResultSubmitRequest;
import com.pw01.webserver.run.dto.ResultSubmitResponse;
import com.pw01.webserver.run.dto.RunStartRequest;
import com.pw01.webserver.run.dto.RunStartResponse;
import com.pw01.webserver.run.service.RunResultService;
import com.pw01.webserver.run.service.RunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판 API(result-api.md R1·R2). 인증 필요(WebConfig). */
@RestController
@RequestMapping("/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunService runService;
    private final RunResultService runResultService;

    /** R1 판 시작. 201(조회 API가 없어 Location은 붙이지 않음) */
    @PostMapping
    public ResponseEntity<RunStartResponse> start(@LoginAccount Long accountId,
                                                  @Valid @RequestBody RunStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(runService.start(accountId, request));
    }

    /** R2 결과 제출. 이미 저장된 판이면 처음 결과를 200으로 */
    @PostMapping("/{runId}/result")
    public ResultSubmitResponse submitResult(@LoginAccount Long accountId, @PathVariable String runId,
                                             @Valid @RequestBody ResultSubmitRequest request) {
        return runResultService.submit(accountId, runId, request);
    }

}
