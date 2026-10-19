package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

/** 401: 누구인지 확인할 수 없다(로그인 실패, 토큰·세션 문제). 예: AUTH_INVALID_CREDENTIALS */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }

}
