package com.pw01.webserver.auth.repository;

import com.pw01.webserver.IntegrationTest;
import com.pw01.webserver.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로그인 실패 저장소: 루트 docs/contracts/redis-keys.md의 pw01:login-fail 키와 login_fail.lua가 명세대로 움직이는지 실제 Redis로 확인한다.
 * 기본 설정(5번·1분·1분)으로 보고, 세는 시간과 잠김 시간이 따로 쓰이는지는 값을 다르게 준 저장소로 본다.
 * 실패하면 login_fail.lua의 인자 순서, pw01.auth.login-fail.* 설정, 아이디 형식 검사를 먼저 의심한다.
 * 테스트끼리 Redis를 함께 쓰므로 아이디를 서로 다르게 쓴다(Fail0001~). 시간은 기다리지 않고 키를 지워 흉내 낸다.
 */
@IntegrationTest
class RedisLoginFailStoreTest {

    @Autowired
    LoginFailStore loginFails;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    AuthProperties properties;

    /** 허용 횟수 전(기본 4번)까지는 잠기지 않고, 키 TTL은 실패를 세는 시간 안이다 */
    @Test
    void failuresBelowLimitDoNotLock() {
        LoginFailResult last = failTimes("Fail0001", maxAttempts() - 1);

        assertThat(last.count()).isEqualTo(maxAttempts() - 1);
        assertThat(last.locked()).isFalse();
        assertThat(loginFails.lockRemaining("Fail0001")).isZero();
        assertThat(redis.getExpire("pw01:login-fail:Fail0001")).isBetween(1L, window().toSeconds());
    }

    /** 허용 횟수째(기본 5번째) 실패: 그 응답부터 잠기고 남은 시간은 잠김 시간이다. 잠긴 동안 lockRemaining도 남은 시간을 준다 */
    @Test
    void failureAtLimitLocks() {
        LoginFailResult last = failTimes("Fail0002", maxAttempts());

        assertThat(last.count()).isEqualTo(maxAttempts());
        assertThat(last.locked()).isTrue();
        assertThat(last.lockRemainingSeconds()).isBetween(lock().toSeconds() - 2, lock().toSeconds());
        assertThat(loginFails.lockRemaining("Fail0002")).isBetween(lock().toSeconds() - 2, lock().toSeconds());
    }

    /** 세는 시간과 잠김 시간은 따로 쓰인다: 세는 동안 TTL은 window, 잠기면 lock으로 다시 건다 */
    @Test
    void windowAndLockAreSeparate() {
        LoginFailStore store = new RedisLoginFailStore(redis, new AuthProperties(
                properties.session(), new AuthProperties.LoginFail(2, Duration.ofSeconds(30), Duration.ofSeconds(90))));

        LoginFailResult first = store.recordFailure("Fail0003");
        long ttlWhileCounting = redis.getExpire("pw01:login-fail:Fail0003");
        LoginFailResult second = store.recordFailure("Fail0003");

        assertThat(first.locked()).isFalse();
        assertThat(ttlWhileCounting).isBetween(28L, 30L);
        assertThat(second.lockRemainingSeconds()).isBetween(88L, 90L);
    }

    /** 실패를 세는 시간이 지나면(키가 만료되어 사라짐) 다시 1부터 센다 */
    @Test
    void countRestartsAfterWindow() {
        failTimes("Fail0004", maxAttempts() - 1);
        redis.delete("pw01:login-fail:Fail0004");

        LoginFailResult next = loginFails.recordFailure("Fail0004");

        assertThat(next.count()).isEqualTo(1);
        assertThat(next.locked()).isFalse();
    }

    /** 로그인 성공 때 clear하면 잠김도 풀린다 */
    @Test
    void clearRemovesLock() {
        failTimes("Fail0005", maxAttempts());

        loginFails.clear("Fail0005");

        assertThat(loginFails.lockRemaining("Fail0005")).isZero();
        assertThat(redis.hasKey("pw01:login-fail:Fail0005")).isFalse();
    }

    /** 아이디는 대소문자를 구분하므로 대소문자만 다른 아이디는 따로 센다(키 둘) */
    @Test
    void loginIdsDifferingInCaseAreCountedSeparately() {
        failTimes("Fail0006", maxAttempts());

        LoginFailResult other = loginFails.recordFailure("fail0006");

        assertThat(other.count()).isEqualTo(1);
        assertThat(loginFails.lockRemaining("fail0006")).isZero();
        assertThat(loginFails.lockRemaining("Fail0006")).isPositive();
    }

    /** 형식 밖 아이디(밑줄, 3자, null)는 키를 만들지 않고 IllegalArgumentException(형식 검사를 먼저 하지 않은 코드 버그) */
    @Test
    void malformedLoginIdIsRejected() {
        assertThatThrownBy(() -> loginFails.recordFailure("fail_07")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loginFails.lockRemaining("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loginFails.clear(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(redis.hasKey("pw01:login-fail:fail_07")).isFalse();
    }

    private LoginFailResult failTimes(String loginId, int times) {
        LoginFailResult last = null;
        for (int i = 0; i < times; i++) {
            last = loginFails.recordFailure(loginId);
        }
        return last;
    }

    private int maxAttempts() {
        return properties.loginFail().maxAttempts();
    }

    private Duration window() {
        return properties.loginFail().window();
    }

    private Duration lock() {
        return properties.loginFail().lock();
    }

}
