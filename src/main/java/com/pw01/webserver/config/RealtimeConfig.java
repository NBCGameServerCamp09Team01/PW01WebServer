package com.pw01.webserver.config;

import com.pw01.webserver.realtime.handler.RealtimeWebSocketHandler;
import com.pw01.webserver.realtime.interceptor.RealtimeHandshakeInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 실시간 연결(S7) 등록. 경로와 규칙은 루트 docs/contracts/realtime-api.md.
 * /realtime은 PublicPaths에 있어 MVC 인증 인터셉터를 거치지 않고, 핸드셰이크 인터셉터가 따로 인증한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeConfig implements WebSocketConfigurer {

    public static final String PATH = "/realtime";

    private final RealtimeWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;

    public RealtimeConfig(RealtimeWebSocketHandler handler, RealtimeHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Origin은 따지지 않는다. UE(libwebsockets)는 Origin에 접속 주소(예: "localhost")를 그대로 실어 보내므로
        // Spring 기본값(같은 출처만)이면 403이 된다. Origin 검사는 브라우저가 쿠키를 자동으로 실어 보내는 경우를 막는 장치인데,
        // 이 연결은 Authorization 헤더로만 인증하고(브라우저 WebSocket은 이 헤더를 붙일 수 없음) 쿠키를 쓰지 않으므로 필요 없다.
        registry.addHandler(handler, PATH)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }

}
