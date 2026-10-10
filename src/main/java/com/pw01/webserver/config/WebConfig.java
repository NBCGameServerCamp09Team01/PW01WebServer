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
 * 인증 인터셉터와 @LoginAccount 등록(뼈대 SF-2).
 * 모든 경로("/**")가 인증을 거치고, 공개 경로(PublicPaths)만 뺀다. 새 인증 API는 여기를 고치지 않아도 된다.
 * 새 공개 API만 PublicPaths에 더한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PublicPaths.ALL);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginAccountArgumentResolver());
    }

}
