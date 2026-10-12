package com.pw01.webserver.progression.requirement;

import java.util.Map;
import java.util.Set;

/**
 * 조건 판정에 쓰는 계정 값 묶음. 요청마다 한 번 읽어(AccountService.facts) 모든 조건에 같이 넘긴다.
 * 판정기는 이 값만 보고 스킬·업적이 무엇인지 모른다(업적·퀘스트도 같은 판정기를 쓸 수 있게).
 *
 * @param version        계정 version(진행 행)
 * @param level          계정 레벨
 * @param statPoints     남은 스탯 포인트
 * @param totals         누적 통계 키 → 값(account_stat_total). 줄이 없는 키는 빠진다
 * @param unlockedSkills 해금한 스킬 태그
 */
public record AccountFacts(long version, int level, int statPoints, Map<String, Long> totals,
                           Set<String> unlockedSkills) {

    public AccountFacts {
        totals = Map.copyOf(totals);
        unlockedSkills = Set.copyOf(unlockedSkills);
    }

    /** 누적 통계 값. 줄이 없으면 null */
    public Long total(String statKey) {
        return totals.get(statKey);
    }

}
