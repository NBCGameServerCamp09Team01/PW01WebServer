package com.pw01.webserver.stageresult.dto;

import com.pw01.webserver.account.dto.AccountSnapshotResponse;

/**
 * 결과 제출 응답 data: 결과 + 저장 상태 + 바뀐 뒤의 계정 스냅샷(README "상태 변경 응답").
 * account는 확장 칸(게임은 쓰지 않아도 됨). 나중에 스테이지 진행을 실을 칸 이름은 stage로 비워 둔다(지금은 없음, v1.1로 더함).
 */
public record ResultSubmitResponse(StageResultResponse result, SaveStatus saveStatus,
                                   AccountSnapshotResponse account) {
}
