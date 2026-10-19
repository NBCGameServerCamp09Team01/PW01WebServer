package com.pw01.webserver.common.web;

/**
 * 성공 응답 본문 {data, meta}. 형식은 루트 docs/contracts/README.md "성공 응답"이 기준이다.
 * 컨트롤러는 DTO를 그대로 돌려주고, ApiResponseAdvice가 이 모양으로 감싼다(컨트롤러에서 직접 만들지 않는다).
 *
 * @param data 그 API의 결과(각 명세의 모양)
 * @param meta 응답 부가 정보
 */
public record ApiResponse<T>(T data, Meta meta) {

    /**
     * @param requestId 요청 번호. 응답 헤더 X-Request-Id와 같다
     */
    public record Meta(String requestId) {
    }

}
