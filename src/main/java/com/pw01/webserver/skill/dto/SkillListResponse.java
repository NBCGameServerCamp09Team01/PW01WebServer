package com.pw01.webserver.skill.dto;

import com.pw01.webserver.progression.requirement.RequirementProgress;

import java.util.List;

/**
 * 스킬 진행도 조회 응답 data(루트 docs/contracts/skill-api.md K2). 칸은 UE FWarriorSkillView에 맞춘다.
 * 이름·설명·아이콘은 보내지 않는다(게임 DA_SkillTree에서 읽음).
 *
 * @param skills     master/skills.json의 스킬 전부, 파일 순서
 * @param statPoints 지금 남은 포인트
 * @param version    계정 version
 */
public record SkillListResponse(List<SkillView> skills, int statPoints, long version) {

    /**
     * @param skillTag     스킬 태그
     * @param state        LOCKED · NEED_POINTS · UNLOCKABLE · UNLOCKED(SkillJudge)
     * @param cost         비용(스탯 포인트)
     * @param requirements 조건 전부의 진행도(선행 → 비용 → 나머지). UNLOCKED도 보낸다(표시용)
     */
    public record SkillView(String skillTag, String state, int cost, List<RequirementProgress> requirements) {
    }

}
