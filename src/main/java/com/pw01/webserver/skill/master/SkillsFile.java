package com.pw01.webserver.skill.master;

import java.util.List;

/**
 * master/skills.json 모양(해금할 수 있는 스킬). 원본은 UE DA_SkillTree이고 손으로 맞춘다(루트 docs/contracts/skill-api.md "스킬").
 *
 * @param skills 해금할 수 있는 스킬 목록. 이 순서대로 진행도 조회(K2)에 나간다
 */
public record SkillsFile(List<Skill> skills) {

    /**
     * @param tag           스킬 태그 = UE 게임플레이 태그 이름(대소문자 구분)
     * @param cost          해금 비용(스탯 포인트, 0 이상)
     * @param prerequisites 먼저 해금해야 하는 스킬 태그(없으면 빈 목록)
     * @param requirements  누적 기록 등 조건(서버가 판정하는 종류만, 없으면 빈 목록)
     */
    public record Skill(String tag, int cost, List<String> prerequisites, List<Requirement> requirements) {

        public Skill {
            prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }

    }

    /**
     * @param type   조건 종류(RequirementTypes, 판정기 표에 있는 것만)
     * @param target 목표 값(0 이상). 시간은 초
     * @param key    조건의 세부 키. 지금 판정하는 종류는 쓰지 않는다(적 종류별 처치처럼 key가 필요한 조건은 판정하지 않음)
     */
    public record Requirement(String type, double target, String key) {
    }

}
