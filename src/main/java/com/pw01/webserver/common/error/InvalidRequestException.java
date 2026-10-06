package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

/** 400: 형식 검증은 통과했지만 Service의 판정에서 받아들일 수 없는 요청 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

}
