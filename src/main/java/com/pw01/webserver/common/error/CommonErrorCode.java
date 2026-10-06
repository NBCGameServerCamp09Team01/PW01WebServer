package com.pw01.webserver.common.error;

/**
 * 모든 API에 공통인 오류 코드. 목록은 루트 docs/contracts/README.md "오류 코드"가 기준이다.
 * 404·405처럼 표에 없는 HTTP 오류는 상태 이름(NOT_FOUND, METHOD_NOT_ALLOWED …)을 코드로 쓴다.
 * 기능별 코드(예: EXAMPLE_NOT_FOUND)는 각 기능 쪽에 두고, 그 명세의 "오류" 표에 먼저 적는다.
 */
public final class CommonErrorCode {

    /** 400: 요청 값(본문 필드, 경로·쿼리 값) 검증 실패. 응답에 필드별 errors가 붙는다 */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** 400: JSON을 읽을 수 없음, 모르는 필드, 타입이 맞지 않음 */
    public static final String INVALID_REQUEST_BODY = "INVALID_REQUEST_BODY";

    /** 500: 서버 내부 오류. 원인은 서버 로그에만 남긴다 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private CommonErrorCode() {
    }

}
