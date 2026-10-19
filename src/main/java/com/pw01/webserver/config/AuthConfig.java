package com.pw01.webserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 인증 설정 값(AuthProperties)을 빈으로 올린다. 아직 쓰는 곳은 없다(회원가입은 Redis를 쓰지 않음).
 * 로그인(A2)~로그아웃(A5)의 Redis 세션·로그인 실패 처리에서 AuthProperties를 주입받아 쓴다. TTL·횟수를 코드에 숫자로 쓰지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {
}
