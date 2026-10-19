package com.pw01.webserver.config;

import com.pw01.webserver.common.server.ServerId;
import com.pw01.webserver.common.web.ServerIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 응답 헤더 X-Server-Id·로그 서버 번호 필터를 등록한다(판 12 W5). 요청 번호 필터(RequestIdFilter, 가장 먼저) 바로 다음에 돈다.
 * @Configuration으로 등록하는 이유는 ServerIdFilter 주석.
 */
@Configuration(proxyBeanMethods = false)
public class ServerIdConfig {

    @Bean
    FilterRegistrationBean<ServerIdFilter> serverIdFilter(ServerId serverId) {
        FilterRegistrationBean<ServerIdFilter> registration = new FilterRegistrationBean<>(new ServerIdFilter(serverId));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

}
