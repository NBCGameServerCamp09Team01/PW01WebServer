package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 409: 지금 상태와 부딪힌다(이미 있음, 이미 처리됨). 예: ACCOUNT_LOGIN_ID_DUPLICATED */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    /** 다시 보내면 될 수 있는 충돌(VERSION_CONFLICT·REQUEST_IN_PROGRESS)은 retryable을 true로 */
    public ConflictException(String code, String message, boolean retryable) {
        super(HttpStatus.CONFLICT, code, message, retryable, null);
    }

    /** 어느 칸이 지금 상태와 부딪혔는지 알릴 때(예: STAT_MAX_EXCEEDED의 스탯). retryable false */
    public ConflictException(String code, String message, List<ErrorResponse.FieldErrorDetail> errors) {
        super(HttpStatus.CONFLICT, code, message, false, errors);
    }

}
