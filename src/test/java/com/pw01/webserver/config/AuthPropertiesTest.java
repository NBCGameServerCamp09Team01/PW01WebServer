package com.pw01.webserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 설정 값(pw01.auth.*)을 읽고 검사하는지 확인한다. 컨테이너 없이 설정만 올린다.
 * 실패하면 AuthProperties의 이름(application.properties의 키와 맞는지)과 값 검사를 먼저 의심한다.
 */
class AuthPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthConfig.class);

    /** 네 값이 이름대로 읽힌다(application.properties와 같은 키) */
    @Test
    void bindsAllValues() {
        runner.withPropertyValues(
                        "pw01.auth.session.ttl=10m",
                        "pw01.auth.login-fail.max-attempts=5",
                        "pw01.auth.login-fail.window=1m",
                        "pw01.auth.login-fail.lock=90s")
                .run(context -> {
                    AuthProperties properties = context.getBean(AuthProperties.class);
                    assertThat(properties.session().ttl()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.loginFail().maxAttempts()).isEqualTo(5);
                    assertThat(properties.loginFail().window()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(properties.loginFail().lock()).isEqualTo(Duration.ofSeconds(90));
                });
    }

    /** 잘못된 값(0초, 초 아래 값, 횟수 0, 값 없음)이면 서버가 뜨지 않는다 */
    @Test
    void rejectsInvalidValues() {
        String[][] invalidCases = {
                {"pw01.auth.session.ttl=0s"},
                {"pw01.auth.login-fail.window=1500ms"},
                {"pw01.auth.login-fail.max-attempts=0"},
                {"pw01.auth.session.ttl="},
        };
        for (String[] invalid : invalidCases) {
            runner.withPropertyValues(
                            "pw01.auth.session.ttl=10m",
                            "pw01.auth.login-fail.max-attempts=5",
                            "pw01.auth.login-fail.window=1m",
                            "pw01.auth.login-fail.lock=1m")
                    .withPropertyValues(invalid)
                    .run(context -> assertThat(context).as(invalid[0]).hasFailed());
        }
    }

}
