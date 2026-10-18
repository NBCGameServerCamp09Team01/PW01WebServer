package com.pw01.webserver.config;

import com.pw01.webserver.common.master.MasterDataLoader;
import com.pw01.webserver.stage.repository.ClasspathStageWaveSource;
import com.pw01.webserver.stage.repository.S3StageWaveSource;
import com.pw01.webserver.stage.repository.StageWaveSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 웨이브 정의를 읽는 곳(StageWaveSource)을 설정 값으로 고른다. classpath면 AWS를 전혀 부르지 않는다(로컬·시험).
 * s3면 S3 클라이언트를 만든다: HTTP 클라이언트는 JDK 내장(url-connection) 하나만 두고(build.gradle에서 다른 것을 뺌),
 * 자격 증명은 SDK 기본 찾기 순서(EC2 인스턴스 역할), 지역은 설정 값.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StageContentProperties.class)
public class StageContentConfig {

    @Bean
    StageWaveSource stageWaveSource(StageContentProperties properties, MasterDataLoader loader) {
        return switch (properties.source()) {
            case CLASSPATH -> new ClasspathStageWaveSource(loader);
            case S3 -> new S3StageWaveSource(S3Client.builder()
                    .region(Region.of(properties.s3().region()))
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build(), properties.s3().bucket(), properties.s3().key());
        };
    }

}
