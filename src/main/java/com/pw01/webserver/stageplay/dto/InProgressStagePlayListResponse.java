package com.pw01.webserver.stageplay.dto;

import com.pw01.webserver.stageplay.entity.StagePlay;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** P5 모든 계정의 진행 중 플레이 목록(data). 칸은 최소: 누가(accountId·nickname) 무엇을(stageId) 언제부터 */
public record InProgressStagePlayListResponse(List<Item> items) {

    public record Item(String stagePlayId, String accountId, String nickname, String stageId, Instant startedAt) {
    }

    /** nicknames: 계정 ID → 닉네임(계정 기능에서 받음) */
    public static InProgressStagePlayListResponse from(List<StagePlay> plays, Map<Long, String> nicknames) {
        return new InProgressStagePlayListResponse(plays.stream()
                .map(play -> new Item(play.getId(), String.valueOf(play.getAccountId()),
                        nicknames.get(play.getAccountId()), play.getStageId(), play.getStartedAt()))
                .toList());
    }

}
