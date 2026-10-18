package com.pw01.webserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 웨이브 정의(stage-waves.json)를 읽는 곳 설정(pw01.stage-content.*). 비밀 값은 없다.
 * 기본(application.properties)은 classpath, 배포(application-prod.properties)는 s3. 틀린 값이면 서버가 뜨지 않는다.
 * file은 로컬 시험 전용이다(AWS 없이 재시작 없는 교체를 PC에서 해 봄, README "로컬 로드 밸런서 환경").
 *
 * @param source     읽는 곳. classpath = 이미지 안 master/(로컬·시험), s3 = S3 객체 하나(prod), file = 디스크 파일 하나(로컬 시험)
 * @param s3         source가 s3일 때의 버킷·키·지역
 * @param file       source가 file일 때의 경로
 * @param safetyPoll s3·file일 때 바뀌었는지 스스로 확인하는 간격(안전망). 서버 사이 알림을 놓친 서버가 이 간격 안에 따라온다
 */
@ConfigurationProperties("pw01.stage-content")
public record StageContentProperties(Source source, S3 s3, FileOptions file, Duration safetyPoll) {

    /** S3 버킷 이름 규칙(소문자·숫자·'.'·'-', 3~63자, 처음과 끝은 소문자·숫자) */
    private static final Pattern BUCKET_NAME = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

    public enum Source { CLASSPATH, S3, FILE }

    /**
     * @param path 파일 경로(예: 컨테이너 안 /content/stage-waves.json, IntelliJ 실행이면 작업 폴더 기준 local/content/stage-waves.json)
     */
    public record FileOptions(String path) {
    }

    /**
     * @param bucket 버킷 이름(예: pw01-content-a1b2c3)
     * @param key    객체 키(예: content/stage-waves.json)
     * @param region 버킷 지역(예: ap-northeast-2). 컨테이너 환경 변수에 지역이 없어 설정으로 준다
     */
    public record S3(String bucket, String key, String region) {
    }

    public StageContentProperties {
        if (source == null) {
            throw new IllegalArgumentException("pw01.stage-content.source는 classpath·s3·file 중 하나여야 합니다.");
        }
        if (safetyPoll == null || safetyPoll.compareTo(Duration.ofSeconds(10)) < 0) {
            throw new IllegalArgumentException("pw01.stage-content.safety-poll은 10초 이상이어야 합니다: " + safetyPoll);
        }
        if (source == Source.S3) {
            if (s3 == null || s3.bucket() == null || !BUCKET_NAME.matcher(s3.bucket()).matches()) {
                throw new IllegalArgumentException("pw01.stage-content.s3.bucket이 비었거나 버킷 이름 모양이 아닙니다: "
                        + (s3 == null ? null : s3.bucket()));
            }
            if (s3.key() == null || s3.key().isBlank() || s3.key().startsWith("/")) {
                throw new IllegalArgumentException("pw01.stage-content.s3.key가 비었거나 '/'로 시작합니다: " + s3.key());
            }
            if (s3.region() == null || s3.region().isBlank()) {
                throw new IllegalArgumentException("pw01.stage-content.s3.region이 비었습니다.");
            }
        }
        if (source == Source.FILE && (file == null || file.path() == null || file.path().isBlank())) {
            throw new IllegalArgumentException("pw01.stage-content.file.path가 비었습니다(source=file).");
        }
    }

}
