package com.pw01.webserver.account.dto;

import com.pw01.webserver.account.entity.AccountProgress;

import java.util.List;
import java.util.Map;

/**
 * 메인화면 값(계정 스냅샷). 로그인 응답과 메인화면 값 조회(A3)가 같이 쓴다.
 * 칸 이름은 UE FWarriorAccountData에 맞춘다(accountLevel). 계정 ID는 문자열.
 * 스탯 분배·해금 스킬 테이블은 S3·S4에서 생기므로 그 전까지 빈 값이다. 누적 경험치는 서버 계산용이라 보내지 않는다.
 */
public record AccountSnapshotResponse(String accountId, Long version, int accountLevel, int experience,
                                      int statPoints, Map<String, Integer> investedStats,
                                      List<String> unlockedSkills) {

    public static AccountSnapshotResponse from(AccountProgress progress) {
        return new AccountSnapshotResponse(
                String.valueOf(progress.getAccountId()),
                progress.getVersion(),
                progress.getLevel(),
                progress.getExperience(),
                progress.getStatPoints(),
                Map.of(),
                List.of());
    }

}
