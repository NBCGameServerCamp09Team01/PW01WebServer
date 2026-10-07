package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 인증 설정 값(pw01.auth.*). 기본값은 application.properties에 있고, 바꿀 때는 그 파일·프로필 파일·환경 변수만 고친다.
 * 키·TTL 규칙은 루트 docs/contracts/redis-keys.md "설정 값" 절이 기준이다.
 * Redis EXPIRE가 초 단위라서 시간은 1초 이상의 초 단위여야 한다. 아니면 서버가 뜨지 않는다.
 *
 * @param session   세션 설정
 * @param loginFail 로그인 실패 잠금 설정
 */
@ConfigurationProperties("pw01.auth")
public record AuthProperties(Session session, LoginFail loginFail) {

    public AuthProperties {
        if (session == null || loginFail == null) {
            throw new IllegalArgumentException("pw01.auth.session과 pw01.auth.login-fail 값이 모두 있어야 합니다");
        }
    }

    /**
     * @param ttl 세션 수명. 인증이 필요한 요청마다 이 값으로 다시 건다. 접속 점검 간격 × 허용 연속 실패보다 길어야 한다
     */
    public record Session(Duration ttl) {

        public Session {
            requireWholeSeconds("pw01.auth.session.ttl", ttl);
        }

    }

    /**
     * @param maxAttempts 허용 실패 횟수. 이 횟수째 실패한 응답부터 잠긴다
     * @param window      실패를 세는 시간. 첫 실패부터 이 시간 안의 실패만 센다
     * @param lock        잠김 시간. 잠긴 때부터 센다
     */
    public record LoginFail(int maxAttempts, Duration window, Duration lock) {

        public LoginFail {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("pw01.auth.login-fail.max-attempts는 1 이상이어야 합니다");
            }
            requireWholeSeconds("pw01.auth.login-fail.window", window);
            requireWholeSeconds("pw01.auth.login-fail.lock", lock);
        }

    }

    private static void requireWholeSeconds(String name, Duration value) {
        if (value == null || value.compareTo(Duration.ofSeconds(1)) < 0 || value.toNanosPart() != 0) {
            throw new IllegalArgumentException(name + "는 1초 이상의 초 단위 값이어야 합니다: " + value);
        }
    }

}
