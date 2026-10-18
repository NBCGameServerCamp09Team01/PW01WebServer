package com.pw01.webserver.stage.repository;

/**
 * 웨이브 정의 파일(stage-waves.json)을 읽는 곳. 로컬·시험은 이미지 안 리소스(ClasspathStageWaveSource),
 * 배포(prod)는 S3(S3StageWaveSource). 나머지 정의 파일 셋(stages·monsters·difficulties)은 늘 리소스에서 읽는다
 * (stage-api.md "서버 정의 파일"). 어느 것을 쓸지는 config/StageContentConfig가 pw01.stage-content.source로 고른다.
 */
public interface StageWaveSource {

    /** 지금 파일 내용. 못 읽으면 StageWaveSourceException */
    StageWaveFile read();

    /** 로그·info에 적을 출처(예: classpath:master/stage-waves.json, s3://버킷/키) */
    String describe();

    /** 실행 중에 바뀔 수 있는 출처인가(S3). 리소스 파일이면 false이고 다시 읽기를 하지 않는다 */
    boolean reloadable();

    /**
     * 지금 객체의 ETag만 본다(내용은 받지 않음, S3 HEAD). 바뀌었을 때만 read()를 부르려고 쓴다.
     * reloadable()이 false면 부르지 않는다. 못 보면 StageWaveSourceException
     */
    String currentEtag();

}
