package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 계정 진행 저장 설정 값(pw01.progress.*). 기본값은 application.properties에 있다. 틀린 값이면 서버가 뜨지 않는다.
 *
 * @param saveAttempts 진행 행 충돌 때 저장을 다시 하는 총 횟수(1 이상). 넘으면 409 VERSION_CONFLICT
 */
@ConfigurationProperties("pw01.progress")
public record ProgressProperties(int saveAttempts) {

    public ProgressProperties {
        if (saveAttempts < 1) {
            throw new IllegalArgumentException("pw01.progress.save-attempts는 1 이상이어야 합니다: " + saveAttempts);
        }
    }

}
