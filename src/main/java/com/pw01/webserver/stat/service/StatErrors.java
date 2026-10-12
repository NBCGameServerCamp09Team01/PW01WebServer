package com.pw01.webserver.stat.service;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.InvalidRequestException;

import java.util.List;

/**
 * 스탯 투자 오류 코드와 예외(루트 docs/contracts/stat-api.md "오류").
 * 오류마다 HTTP 상태(예외 클래스)를 이 파일에서만 정한다(StageErrors·StagePlayErrors·StageResultErrors와 같은 결).
 * errors[].field는 investments[i].statTag 모양(i는 0부터).
 */
public final class StatErrors {

    public static final String STAT_UNKNOWN = "STAT_UNKNOWN";
    public static final String STAT_POINTS_NOT_ENOUGH = "STAT_POINTS_NOT_ENOUGH";
    public static final String STAT_MAX_EXCEEDED = "STAT_MAX_EXCEEDED";
    public static final String STAT_BELOW_ZERO = "STAT_BELOW_ZERO";

    private StatErrors() {
    }

    /** 400: 형식(같은 스탯 두 번·0·묶음 크기) */
    public static InvalidRequestException invalid(List<FieldErrorDetail> errors) {
        return new InvalidRequestException(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.", errors);
    }

    /** 400: 투자할 수 없는 스탯 태그 */
    public static InvalidRequestException unknown(List<FieldErrorDetail> errors) {
        return new InvalidRequestException(STAT_UNKNOWN, "알 수 없는 스탯입니다.", errors);
    }

    /** 409: 묶음 합계 > 남은 포인트 */
    public static ConflictException pointsNotEnough() {
        return new ConflictException(STAT_POINTS_NOT_ENOUGH, "스탯 포인트가 부족합니다.");
    }

    /** 409: 최대 포인트를 넘는 스탯 */
    public static ConflictException maxExceeded(List<FieldErrorDetail> errors) {
        return new ConflictException(STAT_MAX_EXCEEDED, "최대 포인트를 넘는 스탯이 있습니다.", errors);
    }

    /** 409: 투자한 것보다 많이 내림 */
    public static ConflictException belowZero(List<FieldErrorDetail> errors) {
        return new ConflictException(STAT_BELOW_ZERO, "투자한 포인트보다 많이 내릴 수 없습니다.", errors);
    }

    static String field(int index) {
        return "investments[" + index + "].statTag";
    }

}
