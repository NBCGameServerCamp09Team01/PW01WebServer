package com.pw01.webserver.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 공통 오류 응답 본문. 형식은 루트 docs/contracts/README.md "오류 응답"이 기준이다.
 * errors는 검증 오류(VALIDATION_FAILED)일 때만 나가고, 그 밖에는 본문에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String path, List<FieldErrorDetail> errors) {

    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, path, null);
    }

    public static ErrorResponse ofValidation(String path, List<FieldErrorDetail> errors) {
        return new ErrorResponse(CommonErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.", path, errors);
    }

    /** 필드 하나의 검증 오류 */
    public record FieldErrorDetail(String field, String message) {
    }

}
