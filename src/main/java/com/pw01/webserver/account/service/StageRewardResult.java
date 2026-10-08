package com.pw01.webserver.account.service;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;

/**
 * 결과 보상을 반영한 결과(AccountService.grantStageReward). 결과 기능(run)이 응답의 reward·account로 쓴다.
 *
 * @param gain    경험치·레벨·스탯 포인트 변화
 * @param account 반영한 뒤의 계정 스냅샷(version 포함)
 */
public record StageRewardResult(LevelGain gain, AccountSnapshotResponse account) {
}
