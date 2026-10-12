package com.pw01.webserver.progression.requirement;

import com.pw01.webserver.account.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버가 판정하는 조건 표(루트 docs/contracts/skill-api.md "조건 종류"). 한 줄 = 판정기 하나.
 * 여기 없는 종류(사망 방식별·적 종류별 처치, 데미지, 추가 통계)는 재료가 없어 판정하지 않는다(마스터에 쓰면 기동 실패).
 * PREREQUISITE·STAT_POINTS는 마스터 조건 목록이 아니라 선행 목록·비용에서 만들므로 여기 없다(SkillJudge).
 */
@Configuration(proxyBeanMethods = false)
public class RequirementEvaluatorConfig {

    private static final double MILLIS_PER_SECOND = 1000;

    @Bean
    RequirementEvaluator accountLevelEvaluator() {
        return new RequirementEvaluator() {
            @Override
            public String type() {
                return RequirementTypes.ACCOUNT_LEVEL;
            }

            @Override
            public RequirementProgress evaluate(AccountFacts facts, double target) {
                return RequirementProgress.atLeast(type(), null, target, (double) facts.level());
            }
        };
    }

    @Bean
    RequirementEvaluator stagesClearedEvaluator() {
        return StatTotalEvaluator.sumAtLeast(RequirementTypes.STAGES_CLEARED, AccountService.STAT_STAGE_PLAY_CLEARED, 1);
    }

    @Bean
    RequirementEvaluator totalKillsEvaluator() {
        return StatTotalEvaluator.sumAtLeast(RequirementTypes.TOTAL_KILLS, AccountService.STAT_STAGE_PLAY_KILLS, 1);
    }

    @Bean
    RequirementEvaluator playTimeEvaluator() {
        return StatTotalEvaluator.sumAtLeast(RequirementTypes.PLAY_TIME_SECONDS,
                AccountService.STAT_STAGE_PLAY_PLAY_TIME_MS, MILLIS_PER_SECOND);
    }

    @Bean
    RequirementEvaluator maxWaveEvaluator() {
        return StatTotalEvaluator.recordAtLeast(RequirementTypes.MAX_WAVE_REACHED, AccountService.STAT_STAGE_PLAY_MAX_WAVE);
    }

    @Bean
    RequirementEvaluator bestClearTimeEvaluator() {
        return StatTotalEvaluator.recordAtMost(RequirementTypes.BEST_CLEAR_TIME,
                AccountService.STAT_STAGE_PLAY_BEST_CLEAR_MS, MILLIS_PER_SECOND);
    }

}
