package com.pw01.webserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * JPA Auditing(생성·수정 시각 자동 기록).
 * 메인 클래스에 두면 @WebMvcTest 같은 슬라이스 테스트가 JPA 없이 뜨다가 깨지므로 따로 둔다.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /** 시각을 밀리초까지 자른다. 응답 JSON과 DB(DATETIME(3))의 값이 같아진다(루트 docs/contracts 공통 규칙) */
    @Bean
    DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
