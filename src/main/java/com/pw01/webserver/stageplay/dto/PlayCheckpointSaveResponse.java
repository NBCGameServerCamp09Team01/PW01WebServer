package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stageplay.repository.StoredPlayCheckpoint;

import java.time.Instant;

/** C1 체크포인트 저장 응답 data. state는 돌려주지 않는다(게임이 방금 보낸 값) */
public record PlayCheckpointSaveResponse(String stagePlayId, int clearedWave, int contentVersion, double elapsedSeconds,
                                         Instant savedAt) {

    public static PlayCheckpointSaveResponse from(String stagePlayId, StoredPlayCheckpoint checkpoint) {
        return new PlayCheckpointSaveResponse(stagePlayId, checkpoint.clearedWave(), checkpoint.contentVersion(),
                checkpoint.elapsedSeconds(), checkpoint.savedAt());
    }

}
