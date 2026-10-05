package com.pw01.webserver.common.error;

import org.springframework.http.HttpStatus;

/** 503: 의존하는 서비스(DB·Redis 등) 문제로 지금은 처리할 수 없다. 잠시 뒤 다시 보낼 수 있다 */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

}
