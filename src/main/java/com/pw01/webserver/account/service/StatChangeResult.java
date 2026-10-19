package com.pw01.webserver.account.service;

import java.util.List;

/**
 * 스탯 바꿈 판정 결과(AccountService.changeStats). 거절이면 아무것도 저장하지 않은 상태다.
 * 오류 코드·HTTP 상태는 부른 쪽(스탯 기능)이 정한다. 계정 기능은 판정과 저장만 한다.
 *
 * @param outcome    결과 종류
 * @param needPoints 묶음 합계(POINTS_NOT_ENOUGH일 때 의미 있음)
 * @param havePoints 남은 포인트(POINTS_NOT_ENOUGH일 때 의미 있음)
 * @param violations 범위를 벗어난 스탯(MAX_EXCEEDED·BELOW_ZERO일 때)
 */
public record StatChangeResult(Outcome outcome, int needPoints, int havePoints, List<Violation> violations) {

    public enum Outcome {
        /** 이번에 저장함 */
        SAVED,
        /** 이 requestId로 이미 저장돼 있어 아무것도 하지 않음 */
        ALREADY_SAVED,
        /** 묶음 합계 > 남은 포인트 */
        POINTS_NOT_ENOUGH,
        /** 최대 포인트를 넘는 스탯이 있음 */
        MAX_EXCEEDED,
        /** 0 아래로 내려가는 스탯이 있음 */
        BELOW_ZERO
    }

    /** 범위를 벗어난 스탯 하나: 지금 값 + points가 0..maxPoints 밖 */
    public record Violation(StatChange change, int current) {
    }

    static StatChangeResult of(Outcome outcome) {
        return new StatChangeResult(outcome, 0, 0, List.of());
    }

    static StatChangeResult pointsNotEnough(int need, int have) {
        return new StatChangeResult(Outcome.POINTS_NOT_ENOUGH, need, have, List.of());
    }

    static StatChangeResult violations(Outcome outcome, List<Violation> violations) {
        return new StatChangeResult(outcome, 0, 0, List.copyOf(violations));
    }

}
