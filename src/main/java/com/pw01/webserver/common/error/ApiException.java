package com.pw01.webserver.common.error;

import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * 서버가 의도적으로 요청을 거절할 때 던지는 예외의 부모. HTTP 상태와 오류 코드를 함께 들고 다닌다.
 * 직접 쓰지 않고 상태별 하위 클래스(NotFoundException, ConflictException …)를 던진다.
 * 응답 본문은 GlobalExceptionHandler가 만든다.
 * retryable은 보통 상태 코드로 정해진다(5xx만 true). 409 중 다시 보내면 될 수 있는 것(VERSION_CONFLICT 등)만 true로 만든다.
 * errors는 어느 칸이 문제인지 알릴 때만 싣는다(루트 docs/contracts/README.md "오류 응답").
 * extra는 기능 명세가 그 오류 코드에 정한 추가 칸(이름 → 값)이고, 본문 최상위 칸으로 나간다.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;
    private final List<FieldErrorDetail> errors;
    private final Map<String, Object> extra;

    protected ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, status.is5xxServerError(), null);
    }

    protected ApiException(HttpStatus status, String code, String message, boolean retryable,
                           List<FieldErrorDetail> errors) {
        this(status, code, message, retryable, errors, null);
    }

    protected ApiException(HttpStatus status, String code, String message, boolean retryable,
                           List<FieldErrorDetail> errors, Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.errors = errors == null ? null : List.copyOf(errors);
        this.extra = extra == null ? null : Map.copyOf(extra);
    }

}
