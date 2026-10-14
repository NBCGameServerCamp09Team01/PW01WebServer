package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 스테이지 플레이·결과·체크포인트 설정 값(StagePlayProperties·PlayCheckpointProperties)을 빈으로 올린다 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({StagePlayProperties.class, PlayCheckpointProperties.class})
public class StagePlayConfig {
}
