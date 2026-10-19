package com.pw01.webserver.account.dto;

import com.pw01.webserver.account.entity.AccountProgress;

import java.util.List;
import java.util.Map;

/**
 * 메인화면 값(계정 스냅샷). 로그인 응답, 메인화면 값 조회(A3), 계정 값을 바꾸는 요청의 응답 account가 같이 쓴다.
 * 칸 이름은 UE FWarriorAccountData에 맞춘다(accountLevel). 계정 ID는 문자열.
 * investedStats는 투자한 스탯만(0인 스탯은 뺌, S3). unlockedSkills는 해금한 스킬 태그 이름순(S4).
 * 누적 경험치는 서버 계산용이라 보내지 않는다. 만드는 곳은 AccountService 한 곳(getSnapshot 안쪽)이다.
 */
public record AccountSnapshotResponse(String accountId, Long version, int accountLevel, int experience,
                                      int statPoints, Map<String, Integer> investedStats,
                                      List<String> unlockedSkills) {

    public static AccountSnapshotResponse from(AccountProgress progress, Map<String, Integer> investedStats,
                                               List<String> unlockedSkills) {
        return new AccountSnapshotResponse(
                String.valueOf(progress.getAccountId()),
                progress.getVersion(),
                progress.getLevel(),
                progress.getExperience(),
                progress.getStatPoints(),
                Map.copyOf(investedStats),
                List.copyOf(unlockedSkills));
    }

}
