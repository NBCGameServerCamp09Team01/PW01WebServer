package com.pw01.webserver.ranking.dto;

import com.pw01.webserver.ranking.service.RankingBoard;

/**
 * 응답의 board(ranking-api.md "줄 모양"). 진행 보드는 targetId·difficulty가 null(칸은 늘 있음).
 */
public record RankingBoardResponse(String kind, String targetId, Integer difficulty, String period, String periodKey) {

    public static RankingBoardResponse from(RankingBoard board) {
        return new RankingBoardResponse(board.kind().name(), board.targetId(), board.difficulty(),
                board.period().value(), board.periodKey());
    }

}
