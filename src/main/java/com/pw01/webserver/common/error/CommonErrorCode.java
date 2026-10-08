package com.pw01.webserver.common.error;

/**
 * 모든 API에 공통인 오류 코드. 목록은 루트 docs/contracts/README.md "오류 코드"가 기준이다.
 * 404·405처럼 표에 없는 HTTP 오류는 상태 이름(NOT_FOUND, METHOD_NOT_ALLOWED …)을 코드로 쓴다.
 * 기능별 코드(예: ACCOUNT_LOGIN_ID_DUPLICATED)는 각 기능 쪽에 두고, 그 명세의 "오류" 표에 먼저 적는다.
 */
public final class CommonErrorCode {

    /** 400: 요청 값(본문 필드, 경로·쿼리 값) 검증 실패. 응답에 필드별 errors가 붙는다 */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** 400: JSON을 읽을 수 없음, 모르는 필드, 타입이 맞지 않음 */
    public static final String INVALID_REQUEST_BODY = "INVALID_REQUEST_BODY";

    /** 500: 서버 내부 오류. 원인은 서버 로그에만 남긴다 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** 503: MySQL·Redis에 닿지 못함. 잠시 뒤 다시 보내면 될 수 있다 */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    /** 409: 같은 requestId로 다른 본문을 보냄. retryable false(게임 버그) */
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";

    /** 409: 같은 requestId의 처음 요청이 아직 처리 중. retryable true(잠시 뒤 같은 요청) */
    public static final String REQUEST_IN_PROGRESS = "REQUEST_IN_PROGRESS";

    /** 409: 같은 계정의 요청이 동시에 많아 몇 번 다시 해도 저장하지 못함. retryable true */
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";

    private CommonErrorCode() {
    }

}
