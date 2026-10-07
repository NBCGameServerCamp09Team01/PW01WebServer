package com.pw01.webserver.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 429: 요청이 너무 많아 잠시 막았다. 다시 시도할 수 있기까지 남은 시간(초)을 들고 간다.
 * 응답 본문 retryAfterSeconds와 Retry-After 헤더는 GlobalExceptionHandler가 만든다. 예: AUTH_LOGIN_LOCKED
 */
@Getter
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String code, String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

}
