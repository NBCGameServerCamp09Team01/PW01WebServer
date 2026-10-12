package com.pw01.webserver.ranking.service;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.InvalidRequestException;

import java.util.List;

/**
 * 랭킹 오류 코드와 예외. 목록은 루트 docs/contracts/ranking-api.md "오류"가 기준이다.
 * 없는 스테이지·지역은 stage 기능의 STAGE_NOT_FOUND·REGION_NOT_FOUND(StageCatalog)를 그대로 쓴다.
 */
public final class RankingErrors {

    /** 서버가 준 적 없는 커서, 다른 보드의 커서, 주가 바뀐 주간 커서 */
    public static final String RANK_INVALID_CURSOR = "RANK_INVALID_CURSOR";

    private RankingErrors() {
    }

    /** 400: 게임은 커서를 버리고 첫 묶음부터 다시 받는다 */
    public static InvalidRequestException invalidCursor() {
        return new InvalidRequestException(RANK_INVALID_CURSOR, "목록 위치가 올바르지 않습니다. 처음부터 다시 받으세요.");
    }

    /** 400 VALIDATION_FAILED: 쿼리 값(difficulty·period·limit)이 틀림. 문구는 공통 검증 오류와 같다 */
    public static InvalidRequestException invalidQuery(List<FieldErrorDetail> errors) {
        return new InvalidRequestException(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.", errors);
    }

}
