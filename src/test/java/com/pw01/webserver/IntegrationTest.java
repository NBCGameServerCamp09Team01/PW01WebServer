package com.pw01.webserver;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 통합 테스트 묶음: 앱 전체 + MockMvc + test 프로필 + 테스트용 MySQL·Redis 컨테이너.
 * 통합 테스트가 모두 이 애너테이션을 쓰면 Spring이 컨텍스트와 컨테이너를 한 번만 띄워 함께 쓴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MockMvcUtf8Config.class})
public @interface IntegrationTest {
}
