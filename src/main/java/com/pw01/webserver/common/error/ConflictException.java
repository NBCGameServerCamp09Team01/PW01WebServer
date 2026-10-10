package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

/** 409: 지금 상태와 부딪힌다(이미 있음, 이미 처리됨). 예: ACCOUNT_LOGIN_ID_DUPLICATED */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    /** 다시 보내면 될 수 있는 충돌(VERSION_CONFLICT·REQUEST_IN_PROGRESS)은 retryable을 true로 */
    public ConflictException(String code, String message, boolean retryable) {
        super(HttpStatus.CONFLICT, code, message, retryable, null);
    }

}
