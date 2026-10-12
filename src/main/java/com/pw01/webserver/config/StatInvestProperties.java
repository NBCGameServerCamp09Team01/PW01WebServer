package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 스탯 투자 설정 값(pw01.stat-invest.*). 기본값은 application.properties에 있다. 틀린 값이면 서버가 뜨지 않는다.
 *
 * @param maxEntries 묶음 한 번에 담을 수 있는 최대 줄 수(1 이상)
 */
@ConfigurationProperties("pw01.stat-invest")
public record StatInvestProperties(int maxEntries) {

    public StatInvestProperties {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("pw01.stat-invest.max-entries는 1 이상이어야 합니다: " + maxEntries);
        }
    }

}
