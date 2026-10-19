package com.pw01.webserver.progression.requirement;

/**
 * 누적 통계 키 하나를 목표와 견주는 판정(대부분의 조건이 이 모양). RequirementEvaluatorConfig가 표로 등록한다.
 * 저장 단위와 조건 단위가 다르면 scale로 나눈다(예: 밀리초 → 초는 1000).
 *
 * @param type          조건 종류
 * @param statKey       누적 통계 키(AccountService.STAT_*)
 * @param upperBound    true면 "목표 이하"(최고 클리어 시간), false면 "목표 이상"
 * @param scale         저장 값 ÷ scale = 조건 단위
 * @param missingIsZero 줄이 없을 때 0으로 볼지(합계). false면 "기록 없음"(null, 미충족)
 */
public record StatTotalEvaluator(String type, String statKey, boolean upperBound, double scale, boolean missingIsZero)
        implements RequirementEvaluator {

    /** 합계 ≥ 목표(줄이 없으면 0) */
    public static StatTotalEvaluator sumAtLeast(String type, String statKey, double scale) {
        return new StatTotalEvaluator(type, statKey, false, scale, true);
    }

    /** 최고 기록 ≥ 목표(줄이 없으면 기록 없음) */
    public static StatTotalEvaluator recordAtLeast(String type, String statKey) {
        return new StatTotalEvaluator(type, statKey, false, 1, false);
    }

    /** 최고 기록 ≤ 목표(줄이 없으면 기록 없음) */
    public static StatTotalEvaluator recordAtMost(String type, String statKey, double scale) {
        return new StatTotalEvaluator(type, statKey, true, scale, false);
    }

    @Override
    public RequirementProgress evaluate(AccountFacts facts, double target) {
        Long raw = facts.total(statKey);
        // 삼항 연산자로 쓰면 double로 풀리며 null에서 NPE가 나므로 나눠 쓴다
        Double current;
        if (raw != null) {
            current = raw / scale;
        } else {
            current = missingIsZero ? Double.valueOf(0) : null;
        }
        return upperBound
                ? RequirementProgress.atMost(type, null, target, current)
                : RequirementProgress.atLeast(type, null, target, current);
    }

}
