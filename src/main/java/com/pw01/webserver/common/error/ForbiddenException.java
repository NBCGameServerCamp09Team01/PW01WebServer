package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

/** 403: 요청한 사람에게 권한이 없다(다른 계정의 데이터 등) */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

}
