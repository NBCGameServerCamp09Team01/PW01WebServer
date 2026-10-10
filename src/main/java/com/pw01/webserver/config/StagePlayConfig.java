package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 스테이지 플레이·결과 설정 값(StagePlayProperties)을 빈으로 올린다. 시작·결과 서비스가 주입받아 쓴다 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StagePlayProperties.class)
public class StagePlayConfig {
}
