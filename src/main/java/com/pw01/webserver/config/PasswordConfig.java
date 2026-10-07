package com.pw01.webserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호 해시 도구. Spring Security는 crypto(BCrypt)만 쓴다(필터 없이 HandlerInterceptor로 인증) */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}
