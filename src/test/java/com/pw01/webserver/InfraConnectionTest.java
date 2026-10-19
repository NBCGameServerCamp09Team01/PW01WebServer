package com.pw01.webserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 연결 테스트: 테스트용 MySQL·Redis 컨테이너에 실제로 붙는가.
 * 실패하면 드라이버·접속 설정, 컨테이너 이미지 이름, Docker Desktop 상태를 의심한다.
 */
@IntegrationTest
class InfraConnectionTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void mysqlAnswersSelectOne() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        assertThat(one).isEqualTo(1);
    }

    @Test
    void redisAnswersPing() {
        String pong = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);

        assertThat(pong).isEqualTo("PONG");
    }

}
