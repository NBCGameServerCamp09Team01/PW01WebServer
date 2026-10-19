package com.pw01.webserver.skill.dto;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.stageresult.dto.SaveStatus;

import java.util.List;

/**
 * 스킬 해금 응답 data: 해금 결과 + 저장 상태 + 지금 계정 스냅샷(결과 제출·투자 응답과 같은 모양).
 * account는 AccountService.getSnapshot 한 입구로 채운다.
 */
public record SkillUnlockResponse(Result result, SaveStatus saveStatus, AccountSnapshotResponse account) {

    /**
     * @param skillTag       해금한 스킬
     * @param unlockedSkills 반영 뒤 해금한 스킬 전체(태그 이름순)
     * @param statPoints     반영 뒤 남은 포인트
     * @param version        계정 version
     */
    public record Result(String skillTag, List<String> unlockedSkills, int statPoints, Long version) {
    }

    public static SkillUnlockResponse of(String skillTag, SaveStatus saveStatus, AccountSnapshotResponse account) {
        return new SkillUnlockResponse(
                new Result(skillTag, account.unlockedSkills(), account.statPoints(), account.version()),
                saveStatus, account);
    }

}
