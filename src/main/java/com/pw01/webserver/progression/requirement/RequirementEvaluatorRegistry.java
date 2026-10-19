package com.pw01.webserver.progression.requirement;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 조건 종류 → 판정기 표. Spring이 모든 RequirementEvaluator 빈을 모아 만든다(같은 종류가 둘이면 기동 실패).
 * 마스터 검사(SkillCatalog)는 supports로 "판정할 수 있는 종류인가"를 본다.
 */
@Component
public class RequirementEvaluatorRegistry {

    private final Map<String, RequirementEvaluator> byType;

    public RequirementEvaluatorRegistry(List<RequirementEvaluator> evaluators) {
        Map<String, RequirementEvaluator> map = new HashMap<>();
        for (RequirementEvaluator evaluator : evaluators) {
            if (map.put(evaluator.type(), evaluator) != null) {
                throw new IllegalStateException("조건 판정기가 겹칩니다: " + evaluator.type());
            }
        }
        this.byType = Map.copyOf(map);
    }

    public boolean supports(String type) {
        return byType.containsKey(type);
    }

    public Set<String> types() {
        return byType.keySet();
    }

    /** 판정. 모르는 종류는 마스터 검사에서 이미 걸러졌으므로 여기서는 버그(IllegalArgumentException) */
    public RequirementProgress evaluate(String type, AccountFacts facts, double target) {
        RequirementEvaluator evaluator = byType.get(type);
        if (evaluator == null) {
            throw new IllegalArgumentException("판정하지 않는 조건 종류입니다: " + type);
        }
        return evaluator.evaluate(facts, target);
    }

}
