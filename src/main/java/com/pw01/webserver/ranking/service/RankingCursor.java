package com.pw01.webserver.ranking.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * "더 보기" 커서 = 마지막으로 준 줄의 값(ranking-api.md "커서"). 순위 번호가 아니라 값이라
 * 묶음 사이에 기록이 끼어도 줄이 겹치거나 빠지지 않고, Redis·DB 어느 쪽으로 읽어도 같은 자리다.
 * 모양: base64url(패딩 없음)로 감싼 "v1|<보드 id>|<보드 점수>|<Redis 멤버>". 게임은 해석하지 않는다.
 * 다른 보드(대상·난이도·기간·주)의 커서는 받지 않는다 → RANK_INVALID_CURSOR.
 */
public final class RankingCursor {

    private static final String VERSION = "v1";
    private static final int MAX_LENGTH = 200;

    private RankingCursor() {
    }

    public static String encode(RankingBoard board, RankingRow lastRow) {
        String raw = String.join("|", VERSION, board.id(), Long.toString(lastRow.score()), lastRow.member());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 커서 → 마지막 줄. 그 보드의 커서가 아니거나 모양이 틀리면 RANK_INVALID_CURSOR */
    public static RankingRow decode(RankingBoard board, String cursor) {
        if (cursor == null || cursor.isEmpty() || cursor.length() > MAX_LENGTH) {
            throw RankingErrors.invalidCursor();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 4);
            if (parts.length != 4 || !VERSION.equals(parts[0]) || !board.id().equals(parts[1])) {
                throw RankingErrors.invalidCursor();
            }
            long score = Long.parseLong(parts[2]);
            return RankingRow.fromMember(parts[3], -score);
        } catch (IllegalArgumentException e) {
            // Base64 오류·숫자 오류·멤버 모양 오류(NumberFormatException도 IllegalArgumentException)
            throw RankingErrors.invalidCursor();
        }
    }

}
