package com.pw01.webserver.run.service;

import com.pw01.webserver.common.error.CommonErrorCode;
import com.pw01.webserver.common.error.ConflictException;
import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import com.pw01.webserver.common.error.InvalidRequestException;
import com.pw01.webserver.common.error.NotFoundException;

import java.util.List;

/**
 * 판·결과 오류 코드와 예외. 목록은 루트 docs/contracts/result-api.md "오류"가 기준이다.
 * 오류마다 HTTP 상태(예외 클래스)를 이 파일에서만 정한다(StageErrors와 같은 결).
 */
public final class RunErrors {

    public static final String RUN_NOT_FOUND = "RUN_NOT_FOUND";
    public static final String RUN_EXPIRED = "RUN_EXPIRED";
    public static final String RESULT_MISMATCH = "RESULT_MISMATCH";
    public static final String RESULT_INVALID = "RESULT_INVALID";

    private RunErrors() {
    }

    /** 404: 없는 판, 남의 판(있는지 알려 주지 않음) */
    public static NotFoundException runNotFound() {
        return new NotFoundException(RUN_NOT_FOUND, "판을 찾을 수 없습니다.");
    }

    /** 409: 판 유효 시간이 지남. 다시 보내도 같으므로 retryable false */
    public static ConflictException runExpired() {
        return new ConflictException(RUN_EXPIRED, "판의 유효 시간이 지나 결과를 저장할 수 없습니다.");
    }

    /** 400: 판 시작 때와 스테이지·난이도가 다름 */
    public static InvalidRequestException mismatch(List<FieldErrorDetail> errors) {
        return new InvalidRequestException(RESULT_MISMATCH, "판 시작 때의 스테이지·난이도와 다릅니다.", errors);
    }

    /** 400: 타당성 검사 실패(웨이브·시간) */
    public static InvalidRequestException invalid(String field, String message) {
        return new InvalidRequestException(RESULT_INVALID, "결과를 확인할 수 없습니다.",
                List.of(new FieldErrorDetail(field, message)));
    }

    /** 400: 경로의 판 번호를 UUID로 읽지 못함 */
    public static InvalidRequestException badRunId() {
        return new InvalidRequestException(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.",
                List.of(new FieldErrorDetail("runId", "판 번호는 UUID(하이픈 36자)여야 합니다.")));
    }

    /** 409: 같은 계정의 저장이 계속 부딪힘. 다시 보내면 될 수 있으므로 retryable true */
    public static ConflictException versionConflict() {
        return new ConflictException(CommonErrorCode.VERSION_CONFLICT,
                "요청이 몰려 저장하지 못했습니다. 잠시 뒤 다시 보내 주세요.", true);
    }

}
