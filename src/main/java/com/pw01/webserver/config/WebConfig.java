package com.pw01.webserver.config;

import com.pw01.webserver.auth.interceptor.AuthInterceptor;
import com.pw01.webserver.auth.interceptor.LoginAccountArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 인증 인터셉터와 @LoginAccount 등록.
 * 인증이 필요한 경로는 하나씩 적는다(공통 앞부분이 없음). 가입·로그인은 인증 없이 부른다.
 * 새 인증 API를 만들면 여기에 경로를 더한다. 빠뜨리면 그 API에서 @LoginAccount가 500을 낸다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/accounts/**", "/auth/heartbeat", "/auth/logout");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginAccountArgumentResolver());
    }

}
