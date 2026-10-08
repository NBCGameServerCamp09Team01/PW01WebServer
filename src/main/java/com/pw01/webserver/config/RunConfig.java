package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 판·결과 설정 값(RunProperties)을 빈으로 올린다. 판 시작·결과 서비스가 주입받아 쓴다 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RunProperties.class)
public class RunConfig {
}
