package com.pw01.webserver.ranking.controller;

import com.pw01.webserver.auth.interceptor.LoginAccount;
import com.pw01.webserver.ranking.dto.MyRankingResponse;
import com.pw01.webserver.ranking.service.RankingBoardKind;
import com.pw01.webserver.ranking.service.RankingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 순위 API(ranking-api.md RK2·RK4·RK6·RK8). 내 것은 /accounts/me 아래(stage-api.md ST2와 같은 규칙).
 */
@RestController
@RequestMapping("/accounts/me/rankings")
@RequiredArgsConstructor
public class MyRankingController {

    private final RankingQueryService rankingQueryService;

    /** RK2 스테이지 순위에서 내 줄 */
    @GetMapping("/stages/{stageId}")
    public MyRankingResponse stage(@LoginAccount Long accountId, @PathVariable String stageId,
                                   @RequestParam(required = false) Integer difficulty,
                                   @RequestParam(required = false) String period) {
        return rankingQueryService.me(RankingBoardKind.STAGE, stageId, difficulty, period, accountId);
    }

    /** RK4 지역 순위에서 내 줄 */
    @GetMapping("/regions/{regionId}")
    public MyRankingResponse region(@LoginAccount Long accountId, @PathVariable String regionId,
                                    @RequestParam(required = false) Integer difficulty,
                                    @RequestParam(required = false) String period) {
        return rankingQueryService.me(RankingBoardKind.REGION, regionId, difficulty, period, accountId);
    }

    /** RK8 전체 총합 순위에서 내 줄 */
    @GetMapping("/total")
    public MyRankingResponse total(@LoginAccount Long accountId,
                                   @RequestParam(required = false) Integer difficulty,
                                   @RequestParam(required = false) String period) {
        return rankingQueryService.me(RankingBoardKind.TOTAL, null, difficulty, period, accountId);
    }

    /** RK6 진행 순위에서 내 줄 */
    @GetMapping("/progress")
    public MyRankingResponse progress(@LoginAccount Long accountId,
                                      @RequestParam(required = false) String period) {
        return rankingQueryService.me(RankingBoardKind.PROGRESS, null, null, period, accountId);
    }

}
