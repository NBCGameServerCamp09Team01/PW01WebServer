package com.pw01.webserver.stageplay.repository;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Redis pw01:play-checkpoint:<stagePlayId>에 JSON으로 저장하는 값(루트 docs/contracts/redis-keys.md "키 목록").
 * accountId는 점검용이다(주인 확인은 MySQL stage_play로 먼저 한다). state는 게임이 정한 JSON 객체 그대로.
 */
public record StoredPlayCheckpoint(String accountId, int clearedWave, int contentVersion, double elapsedSeconds,
                                   Instant savedAt, JsonNode state) {
}
