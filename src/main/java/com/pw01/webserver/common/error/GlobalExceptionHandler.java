package com.pw01.webserver.common.error;

import com.pw01.webserver.common.error.ErrorResponse.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.List;

/**
 * 모든 오류를 공통 오류 응답(ErrorResponse)으로 바꾼다. 형식과 코드는 루트 docs/contracts/README.md가 기준이다.
 * Spring MVC 표준 오류(없는 경로, 허용되지 않은 메서드 등)는 부모 클래스가 잡고, 여기서는 본문만 바꾼다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 429: 본문에 남은 시간을 싣고 표준 Retry-After 헤더도 붙인다(ApiException보다 구체적인 타입이라 먼저 고른다) */
    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException e, HttpServletRequest request) {
        logRejected(request.getMethod(), request.getRequestURI(), e.getStatus().value(), e.getCode());
        return ResponseEntity.status(e.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ErrorResponse.ofRetryAfter(e.getCode(), e.getMessage(), request.getRequestURI(),
                        e.getRetryAfterSeconds()));
    }

    /** 서버가 의도적으로 거절한 경우(상태별 예외) */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException e, HttpServletRequest request) {
        if (e.getStatus().is4xxClientError()) {
            logRejected(request.getMethod(), request.getRequestURI(), e.getStatus().value(), e.getCode());
        }
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(e.getCode(), e.getMessage(), request.getRequestURI(), e.getStatus()));
    }

    /**
     * 503: MySQL·Redis에 닿지 못함. 대상은 두 가지만이다(회의 10/7).
     * DataAccessResourceFailureException: DB·Redis 연결 실패(RedisConnectionFailureException도 이 하위).
     * CannotCreateTransactionException: 트랜잭션을 시작할 DB 연결을 못 얻음(DataAccessException이 아니라 따로 적는다).
     * 제약 위반(DataIntegrityViolationException)처럼 연결은 됐는데 실패한 것은 500으로 둔다.
     */
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> handleUnavailable(Exception e, HttpServletRequest request) {
        log.error("저장소에 닿지 못함: {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(CommonErrorCode.SERVICE_UNAVAILABLE,
                        "잠시 뒤 다시 시도해 주세요.", request.getRequestURI(), HttpStatus.SERVICE_UNAVAILABLE));
    }

    /** 예상하지 못한 오류. 원인은 서버 로그에만 남기고 응답 본문에는 넣지 않는다 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리하지 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR, "서버 내부 오류입니다.", request.getRequestURI(),
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** 본문 필드 검증 실패(@Valid @RequestBody) */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        logRejected(null, path(request), 400, CommonErrorCode.VALIDATION_FAILED);
        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(path(request), errors));
    }

    /** 경로·쿼리 값 검증 실패(@PathVariable·@RequestParam에 붙인 제약) */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        logRejected(null, path(request), 400, CommonErrorCode.VALIDATION_FAILED);
        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(path(request), errors));
    }

    /** 경로·쿼리 값의 형식이 틀림(예: 숫자 자리에 문자를 보냄) */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        List<FieldErrorDetail> errors = List.of(new FieldErrorDetail(field, "값의 형식이 올바르지 않습니다."));
        logRejected(null, path(request), 400, CommonErrorCode.VALIDATION_FAILED);
        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(path(request), errors));
    }

    /** 필수 쿼리 값이 없음 */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = List.of(new FieldErrorDetail(ex.getParameterName(), "값이 필요합니다."));
        logRejected(null, path(request), 400, CommonErrorCode.VALIDATION_FAILED);
        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(path(request), errors));
    }

    /** 본문을 읽을 수 없음: JSON 문법 오류, 모르는 필드, 타입 불일치 */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getMostSpecificCause() instanceof UnrecognizedPropertyException unknown
                ? "모르는 필드가 있습니다: " + unknown.getPropertyName()
                : "요청 본문을 읽을 수 없습니다.";
        logRejected(null, path(request), 400, CommonErrorCode.INVALID_REQUEST_BODY);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(CommonErrorCode.INVALID_REQUEST_BODY, message, path(request)));
    }

    /** 그 밖의 Spring MVC 표준 오류(404·405·415 등). 500은 INTERNAL_ERROR, 나머지는 상태 이름을 코드로 쓴다 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("처리하지 못한 오류: {}", path(request), ex);
        } else {
            logRejected(null, path(request), statusCode.value(), ex.getClass().getSimpleName());
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code;
        if (statusCode.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            code = CommonErrorCode.INTERNAL_ERROR;
        } else {
            code = status != null ? status.name() : "HTTP_" + statusCode.value();
        }
        String message = switch (statusCode.value()) {
            case 404 -> "요청한 경로가 없습니다.";
            case 405 -> "허용되지 않은 HTTP 메서드입니다.";
            case 415 -> "지원하지 않는 Content-Type입니다.";
            case 500 -> "서버 내부 오류입니다.";
            default -> "요청을 처리할 수 없습니다.";
        };
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ErrorResponse.of(code, message, path(request), statusCode));
    }

    /** 4xx는 WARN 한 줄(스택 없음). 요청 번호는 로그 패턴의 MDC(requestId)로 붙는다. 본문·토큰은 남기지 않는다 */
    private static void logRejected(String method, String path, int status, String reason) {
        log.warn("요청 거절: {} {} -> {} {}", method == null ? "" : method, path, status, reason);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }

}
