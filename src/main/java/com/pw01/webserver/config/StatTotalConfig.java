package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 누적 통계 대조 설정 값(StatTotalProperties)을 빈으로 올린다 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StatTotalProperties.class)
public class StatTotalConfig {
}
