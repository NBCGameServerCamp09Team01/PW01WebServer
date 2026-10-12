package com.pw01.webserver.skill.service;

import com.pw01.webserver.progression.requirement.AccountFacts;
import com.pw01.webserver.progression.requirement.RequirementEvaluatorRegistry;
import com.pw01.webserver.progression.requirement.RequirementProgress;
import com.pw01.webserver.progression.requirement.RequirementTypes;
import com.pw01.webserver.skill.master.SkillsFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 스킬 하나의 조건 전부를 판정한다: 선행 스킬 → 비용(스탯 포인트) → 마스터 조건 순서(루트 docs/contracts/skill-api.md).
 * 선행·비용은 마스터의 선행 목록·비용에서 만들고, 나머지는 판정기 표(RequirementEvaluatorRegistry)에 맡긴다.
 * 해금(K1)과 진행도 조회(K2)가 같은 판정을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SkillJudge {

    /** 진행도 조회의 상태 이름(UE EWarriorSkillState) */
    public static final String LOCKED = "LOCKED";
    public static final String NEED_POINTS = "NEED_POINTS";
    public static final String UNLOCKABLE = "UNLOCKABLE";
    public static final String UNLOCKED = "UNLOCKED";

    private final RequirementEvaluatorRegistry registry;

    public List<RequirementProgress> judge(SkillsFile.Skill skill, AccountFacts facts) {
        List<RequirementProgress> list = new ArrayList<>();
        for (String prerequisite : skill.prerequisites()) {
            double current = facts.unlockedSkills().contains(prerequisite) ? 1 : 0;
            list.add(RequirementProgress.atLeast(RequirementTypes.PREREQUISITE, prerequisite, 1, current));
        }
        list.add(RequirementProgress.atLeast(RequirementTypes.STAT_POINTS, null, skill.cost(),
                (double) facts.statPoints()));
        for (SkillsFile.Requirement requirement : skill.requirements()) {
            list.add(registry.evaluate(requirement.type(), facts, requirement.target()));
        }
        return list;
    }

    /**
     * 상태: 해금함 → UNLOCKED, 선행·조건 중 미충족 → LOCKED, 포인트만 모자람 → NEED_POINTS, 다 됨 → UNLOCKABLE.
     * requirements는 judge 결과
     */
    public String state(SkillsFile.Skill skill, AccountFacts facts, List<RequirementProgress> requirements) {
        if (facts.unlockedSkills().contains(skill.tag())) {
            return UNLOCKED;
        }
        boolean otherUnmet = requirements.stream()
                .anyMatch(r -> !r.met() && !RequirementTypes.STAT_POINTS.equals(r.type()));
        if (otherUnmet) {
            return LOCKED;
        }
        boolean pointsUnmet = requirements.stream().anyMatch(r -> !r.met());
        return pointsUnmet ? NEED_POINTS : UNLOCKABLE;
    }

}
