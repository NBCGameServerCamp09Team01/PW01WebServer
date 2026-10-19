package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 계정 값을 바꾸는 요청(S3~)이 같이 쓰는 설정 값을 빈으로 올린다:
 * 진행 저장 재시도(ProgressProperties), 중복 방지(IdempotencyProperties), 스탯 투자(StatInvestProperties)
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ProgressProperties.class, IdempotencyProperties.class, StatInvestProperties.class})
public class ProgressConfig {
}
