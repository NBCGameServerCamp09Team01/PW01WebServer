package com.pw01.webserver.config;

import com.pw01.webserver.stage.service.StageCatalog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 길드 설정 값(GuildProperties)을 빈으로 올리고, 마스터 데이터와 맞는지 뜰 때 확인한다.
 * 만들기 조건 스테이지가 마스터 데이터에 없으면 아무도 길드를 만들 수 없으므로 서버가 뜨지 않게 한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GuildProperties.class)
public class GuildConfig {

    public GuildConfig(GuildProperties properties, StageCatalog stageCatalog) {
        if (stageCatalog.find(properties.createRequiredStage()).isEmpty()) {
            throw new IllegalStateException("pw01.guild.create-required-stage가 마스터 데이터에 없습니다: "
                    + properties.createRequiredStage());
        }
    }

}
