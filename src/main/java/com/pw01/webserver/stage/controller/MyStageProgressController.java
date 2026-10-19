package com.pw01.webserver.stage.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.stage.dto.StageProgressListResponse;
import com.pw01.webserver.stage.service.StageProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 스테이지 진행 API(stage-api.md ST2). /accounts/** 라 인증 인터셉터를 거친다(WebConfig).
 */
@RestController
@RequestMapping("/accounts/me/stages")
@RequiredArgsConstructor
public class MyStageProgressController {

    private final StageProgressService stageProgressService;

    /** ST2 스테이지마다 내 상태(LOCKED·OPEN·CLEARED)와 처음 클리어 시각. 선택 화면을 열 때마다 다시 받는다 */
    @GetMapping
    public StageProgressListResponse myProgress(@LoginAccount Long accountId) {
        return stageProgressService.getMyProgress(accountId);
    }

}
