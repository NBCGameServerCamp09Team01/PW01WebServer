package com.pw01.webserver.ranking.controller;

import com.pw01.webserver.ranking.dto.RankingListResponse;
import com.pw01.webserver.ranking.service.RankingBoardKind;
import com.pw01.webserver.ranking.service.RankingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 순위 목록 API(ranking-api.md RK1·RK3·RK5·RK7). 공개 경로가 아니라 인증 인터셉터를 거친다(WebConfig "/**").
 * 쿼리 값 판정(difficulty·period·limit·cursor)은 RankingQueryService가 한다. 숫자 자리에 문자가 오면 공통 처리(VALIDATION_FAILED).
 */
@RestController
@RequestMapping("/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingQueryService rankingQueryService;

    /** RK1 스테이지 순위 */
    @GetMapping("/stages/{stageId}")
    public RankingListResponse stage(@PathVariable String stageId,
                                     @RequestParam(required = false) Integer difficulty,
                                     @RequestParam(required = false) String period,
                                     @RequestParam(required = false) Integer limit,
                                     @RequestParam(required = false) String cursor) {
        return rankingQueryService.list(RankingBoardKind.STAGE, stageId, difficulty, period, limit, cursor);
    }

    /** RK3 지역 순위 */
    @GetMapping("/regions/{regionId}")
    public RankingListResponse region(@PathVariable String regionId,
                                      @RequestParam(required = false) Integer difficulty,
                                      @RequestParam(required = false) String period,
                                      @RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) String cursor) {
        return rankingQueryService.list(RankingBoardKind.REGION, regionId, difficulty, period, limit, cursor);
    }

    /** RK5 진행 순위(전체 기간만) */
    @GetMapping("/progress")
    public RankingListResponse progress(@RequestParam(required = false) String period,
                                        @RequestParam(required = false) Integer limit,
                                        @RequestParam(required = false) String cursor) {
        return rankingQueryService.list(RankingBoardKind.PROGRESS, null, null, period, limit, cursor);
    }

    /** RK7 전체 총합(모든 지역) 순위 */
    @GetMapping("/total")
    public RankingListResponse total(@RequestParam(required = false) Integer difficulty,
                                     @RequestParam(required = false) String period,
                                     @RequestParam(required = false) Integer limit,
                                     @RequestParam(required = false) String cursor) {
        return rankingQueryService.list(RankingBoardKind.TOTAL, null, difficulty, period, limit, cursor);
    }

}
