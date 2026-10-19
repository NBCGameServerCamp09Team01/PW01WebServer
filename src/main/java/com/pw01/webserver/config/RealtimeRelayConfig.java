package com.pw01.webserver.config;

import com.pw01.webserver.realtime.service.RealtimeRelayListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

/**
 * 실시간 서버 사이 전달(M23 뼈대) 구독 설정. 채널은 루트 docs/contracts/redis-keys.md "실시간 서버 사이 전달".
 * 모든 서버가 전체·지정 채널과 길드 채널 패턴 하나를 듣는다. 구독은 컨테이너가 평소 명령과 다른 전용 연결로 한다.
 * pw01.realtime.relay.enabled=false면 구독하지 않는다(보내기도 RealtimeRelayPublisher가 건너뛴다).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RealtimeRelayProperties.class)
public class RealtimeRelayConfig implements DisposableBean {

    /**
     * 받기 스레드. 하나로 두어 한 서버에서 온 메시지를 받은 순서대로 보낸다(컨테이너 기본은 메시지마다 새 스레드라 순서가 섞일 수 있다).
     * Executor를 빈으로 내놓으면 Spring Boot의 기본 작업 실행기(applicationTaskExecutor)가 만들어지지 않으므로 여기서만 들고 직접 닫는다.
     */
    private ThreadPoolTaskExecutor relayExecutor;

    @Bean
    @ConditionalOnProperty(name = "pw01.realtime.relay.enabled", havingValue = "true", matchIfMissing = true)
    RedisMessageListenerContainer realtimeRelayContainer(RedisConnectionFactory connectionFactory,
                                                         RealtimeRelayListener listener,
                                                         RealtimeRelayProperties properties) {
        relayExecutor = new ThreadPoolTaskExecutor();
        relayExecutor.setCorePoolSize(1);
        relayExecutor.setMaxPoolSize(1);
        relayExecutor.setThreadNamePrefix("realtime-relay-");
        relayExecutor.initialize();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(relayExecutor);
        container.addMessageListener(listener, List.of(
                new ChannelTopic(properties.globalChannel()),
                new ChannelTopic(properties.targetChannel()),
                new PatternTopic(properties.guildChannelPrefix() + "*")));
        return container;
    }

    @Override
    public void destroy() {
        if (relayExecutor != null) {
            relayExecutor.shutdown();
        }
    }

}
