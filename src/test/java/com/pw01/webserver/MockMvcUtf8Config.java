package com.pw01.webserver;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

/**
 * MockMvc가 응답 본문을 UTF-8로 읽게 한다. JSON 응답에는 charset이 붙지 않는데,
 * MockMvc의 기본값은 ISO-8859-1이라 그대로 두면 한글 메시지 비교가 깨진다(서버 응답은 UTF-8이 맞다).
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockMvcUtf8Config {

    @Bean
    MockMvcBuilderCustomizer utf8ResponseCustomizer() {
        return builder -> builder.defaultResponseCharacterEncoding(StandardCharsets.UTF_8);
    }

}
