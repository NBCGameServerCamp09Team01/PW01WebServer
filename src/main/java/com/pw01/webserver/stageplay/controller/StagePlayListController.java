package com.pw01.webserver.stageplay.controller;

import com.pw01.webserver.stageplay.dto.InProgressStagePlayListResponse;
import com.pw01.webserver.stageplay.service.StagePlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 계정의 스테이지 플레이 목록(공유 초안 P5). 로그인한 누구나 부른다(인증 필요, 관리자 구분은 아직 없음).
 * 칸은 최소(누가·무엇을·언제부터)만 내보낸다.
 */
@RestController
@RequestMapping("/stage-plays")
@RequiredArgsConstructor
public class StagePlayListController {

    private final StagePlayService stagePlayService;

    /** P5 진행 중 플레이 목록. status는 지금 IN_PROGRESS만(없으면 IN_PROGRESS) */
    @GetMapping
    public InProgressStagePlayListResponse list(@RequestParam(defaultValue = "IN_PROGRESS") String status) {
        return stagePlayService.listInProgress(status);
    }

}
