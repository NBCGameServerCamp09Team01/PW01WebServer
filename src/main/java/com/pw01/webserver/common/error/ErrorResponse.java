package com.pw01.webserver.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.http.HttpStatusCode;

import java.util.List;

/**
 * 공통 오류 응답 본문. 형식은 루트 docs/contracts/README.md "오류 응답"이 기준이다.
 * retryable은 늘 나간다: 같은 요청을 다시 보내도 되는지(서버 쪽 문제 500·503만 true).
 * errors는 검증 오류(VALIDATION_FAILED)일 때만, retryAfterSeconds는 429일 때만 나가고, 그 밖에는 본문에서 빠진다.
 * 오류 본문은 성공 응답처럼 {data, meta}로 감싸지 않는다(요청 번호는 X-Request-Id 헤더로 나감).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String path, boolean retryable,
                            List<FieldErrorDetail> errors, Long retryAfterSeconds) {

    /** 다시 보내도 소용없는 거절(4xx) */
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, path, false, null, null);
    }

    /** 상태 코드로 retryable을 정한다: 5xx면 true */
    public static ErrorResponse of(String code, String message, String path, HttpStatusCode status) {
        return new ErrorResponse(code, message, path, status.is5xxServerError(), null, null);
    }

    /** 상태별 예외가 정한 retryable·errors를 그대로 싣는다(GlobalExceptionHandler의 ApiException 처리) */
    public static ErrorResponse of(String code, String message, String path, boolean retryable,
                                   List<FieldErrorDetail> errors) {
        return new ErrorResponse(code, message, path, retryable, errors, null);
    }

    public static ErrorResponse ofValidation(String path, List<FieldErrorDetail> errors) {
        return new ErrorResponse(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.", path, false, errors, null);
    }

    /** 429: 다시 시도할 수 있기까지 남은 시간(초)을 싣는다. 시간이 지나야 하므로 retryable은 false */
    public static ErrorResponse ofRetryAfter(String code, String message, String path, long retryAfterSeconds) {
        return new ErrorResponse(code, message, path, false, null, retryAfterSeconds);
    }

    /** 필드 하나의 검증 오류 */
    public record FieldErrorDetail(String field, String message) {
    }

}
