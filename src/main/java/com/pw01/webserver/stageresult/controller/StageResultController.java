package com.pw01.webserver.stageresult.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.stageresult.dto.ResultSubmitRequest;
import com.pw01.webserver.stageresult.dto.ResultSubmitResponse;
import com.pw01.webserver.stageresult.service.StageResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결과 제출 API. 스테이지 플레이 밑에 둔다(플레이 하나에 결과 하나). 인증 필요(WebConfig "/**").
 * 다시 받기(GET 같은 경로)는 결과 기능 담당(c)이 더한다.
 */
@RestController
@RequestMapping("/accounts/me/stage-plays/{stagePlayId}/result")
@RequiredArgsConstructor
public class StageResultController {

    private final StageResultService stageResultService;

    /** 결과 제출. 이미 저장된 플레이면 처음 결과를 200으로 */
    @PostMapping
    public ResultSubmitResponse submit(@LoginAccount Long accountId, @PathVariable String stagePlayId,
                                       @Valid @RequestBody ResultSubmitRequest request) {
        return stageResultService.submit(accountId, stagePlayId, request);
    }

}
