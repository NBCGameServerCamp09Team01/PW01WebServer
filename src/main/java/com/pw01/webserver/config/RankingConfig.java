package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 랭킹 설정 값(RankingProperties)을 빈으로 올린다. ranking 패키지가 주입받아 쓴다 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RankingProperties.class)
public class RankingConfig {
}
