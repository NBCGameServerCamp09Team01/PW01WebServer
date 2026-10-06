package com.pw01.webserver.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서버가 의도적으로 요청을 거절할 때 던지는 예외의 부모. HTTP 상태와 오류 코드를 함께 들고 다닌다.
 * 직접 쓰지 않고 상태별 하위 클래스(NotFoundException, ConflictException …)를 던진다.
 * 응답 본문은 GlobalExceptionHandler가 만든다.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

}
