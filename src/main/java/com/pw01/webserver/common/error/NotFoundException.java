package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

/** 404: 찾는 대상이 없다(없는 ID 등). 예: EXAMPLE_NOT_FOUND */
public class NotFoundException extends ApiException {

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

}
