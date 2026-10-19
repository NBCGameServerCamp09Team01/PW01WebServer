package com.pw01.webserver.stage.repository;

/**
 * 웨이브 정의 파일을 출처(S3)에서 읽지 못했다. 기동 중이면 서버가 뜨지 않는다(배포 스크립트가 이전 컨테이너로 되돌림).
 * 메시지에 출처·상태 코드·짐작되는 원인을 담아 로그 한 줄로 원인을 찾게 한다.
 */
public class StageWaveSourceException extends RuntimeException {

    public StageWaveSourceException(String message, Throwable cause) {
        super(message, cause);
    }

}
