package com.pw01.webserver.stage.controller;

import com.pw01.webserver.stage.dto.StageListResponse;
import com.pw01.webserver.stage.service.StageCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스테이지 정의 API(stage-api.md ST1). 모두에게 같은 값이라 인증 없이 부른다(WebConfig 인증 경로에 없음).
 * 계정의 진행은 MyStageProgressController(ST2, /accounts/me/stages).
 */
@RestController
@RequestMapping("/stages")
@RequiredArgsConstructor
public class StageController {

    private final StageCatalog stageCatalog;

    /** ST1 지역 → 스테이지 정의. 값은 서버 기동 뒤 바뀌지 않으므로 게임은 한 번 받아 두면 된다 */
    @GetMapping
    public StageListResponse list() {
        return StageListResponse.from(stageCatalog.regions(), stageCatalog.totalCount());
    }

}
