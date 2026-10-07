package com.pw01.webserver.common.web;

import com.pw01.webserver.common.error.ErrorResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 성공 응답을 {data, meta}로 감싼다(루트 docs/contracts/README.md "성공 응답"). 컨트롤러는 DTO를 그대로 돌려준다.
 * 감싸지 않는 것: 오류 본문(ErrorResponse), 본문 없음(204), JSON이 아닌 응답, 이 앱 밖의 컨트롤러(actuator·Swagger),
 * 학습용 예시 API(example 패키지, 규칙보다 먼저 만듦. S1 병합 때 지움).
 */
@RestControllerAdvice(basePackages = "com.pw01.webserver")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String EXAMPLE_PACKAGE = "com.pw01.webserver.example";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJacksonHttpMessageConverter.class.isAssignableFrom(converterType)
                && !returnType.getContainingClass().getPackageName().startsWith(EXAMPLE_PACKAGE);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || body instanceof ErrorResponse || body instanceof ApiResponse<?>) {
            return body;
        }
        return new ApiResponse<>(body, new ApiResponse.Meta(requestId(request)));
    }

    private static String requestId(ServerHttpRequest request) {
        return request instanceof ServletServerHttpRequest servlet
                ? RequestIdFilter.requestId(servlet.getServletRequest())
                : null;
    }

}
