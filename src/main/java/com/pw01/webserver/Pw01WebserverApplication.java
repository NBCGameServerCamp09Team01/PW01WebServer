package com.pw01.webserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class Pw01WebserverApplication {

    public static void main(String[] args) {
        // 서버 시각 기준은 UTC(docs/decisions/0001 W14). 테스트는 build.gradle에서 같은 값을 준다
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(Pw01WebserverApplication.class, args);
    }

}
