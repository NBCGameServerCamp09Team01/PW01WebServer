package com.pw01.webserver.stat.dto;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;
import com.pw01.webserver.stageresult.dto.SaveStatus;

import java.util.Map;

/**
 * 묶음 투자 응답 data: 투자 결과 + 저장 상태 + 지금 계정 스냅샷(결과 제출 응답과 같은 모양).
 * account는 AccountService.getSnapshot 한 입구로 채운다(새 조립기 없음, 지시서 3장).
 */
public record StatInvestResponse(Result result, SaveStatus saveStatus, AccountSnapshotResponse account) {

    /**
     * @param statPoints    반영 뒤 남은 포인트
     * @param investedStats 반영 뒤 전체 투자 값(0인 스탯은 빠짐)
     * @param version       계정 version
     */
    public record Result(int statPoints, Map<String, Integer> investedStats, Long version) {
    }

    public static StatInvestResponse of(SaveStatus saveStatus, AccountSnapshotResponse account) {
        return new StatInvestResponse(new Result(account.statPoints(), account.investedStats(), account.version()),
                saveStatus, account);
    }

}
