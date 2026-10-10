package com.pw01.webserver.config;

import java.util.List;

/**
 * 인증 없이 부르는 경로(뼈대 SF-2). 이 목록 밖의 모든 경로는 인증 인터셉터를 거친다(WebConfig "/**").
 * 새 공개 API를 만들면 여기에 더하고, 그 명세의 엔드포인트 표 "인증" 칸을 "없음"으로 적는다.
 * 빠뜨리면 그 API는 토큰이 없을 때 401이 된다(반대로 인증 API를 빠뜨려 @LoginAccount가 500이 되는 일은 없어진다).
 */
public final class PublicPaths {

    public static final List<String> ALL = List.of(
            "/auth/signup",          // A1 가입(auth-api.md)
            "/auth/login",           // A2 로그인(auth-api.md)
            "/stages",               // ST1 스테이지 정의(stage-api.md)
            "/realtime",             // WebSocket 핸드셰이크(realtime-api.md). 인증은 핸드셰이크 쪽이 따로 함
            "/error",                // Spring 오류 경로
            "/actuator/**",          // 상태 확인
            "/swagger-ui/**",        // API 보기(springdoc)
            "/swagger-ui.html",
            "/v3/api-docs/**"
    );

    private PublicPaths() {
    }

}
