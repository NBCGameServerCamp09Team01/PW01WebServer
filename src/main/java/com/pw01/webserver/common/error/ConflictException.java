package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

/** 409: 지금 상태와 부딪힌다(이미 있음, 이미 처리됨). 예: EXAMPLE_NAME_DUPLICATED */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

}
