package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stageplay.repository.StoredPlayCheckpoint;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** C2 체크포인트 받기 응답 data. state는 C1로 올린 그대로 */
public record PlayCheckpointResponse(String stagePlayId, int clearedWave, int contentVersion, double elapsedSeconds,
                                     Instant savedAt, JsonNode state) {

    public static PlayCheckpointResponse from(String stagePlayId, StoredPlayCheckpoint checkpoint) {
        return new PlayCheckpointResponse(stagePlayId, checkpoint.clearedWave(), checkpoint.contentVersion(),
                checkpoint.elapsedSeconds(), checkpoint.savedAt(), checkpoint.state());
    }

}
