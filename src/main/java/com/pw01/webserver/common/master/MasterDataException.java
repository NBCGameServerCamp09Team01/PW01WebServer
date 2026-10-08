package com.pw01.webserver.common.master;

/**
 * 마스터 데이터(src/main/resources/master/*.json)가 없거나 틀렸다. 기동 중에 던져져 서버가 뜨지 않는다.
 * 메시지에 파일 이름과 이유를 담아 기동 로그에서 바로 원인을 찾게 한다.
 */
public class MasterDataException extends RuntimeException {

    public MasterDataException(String fileName, String reason) {
        super(format(fileName, reason));
    }

    public MasterDataException(String fileName, String reason, Throwable cause) {
        super(format(fileName, reason), cause);
    }

    private static String format(String fileName, String reason) {
        return "[master/" + fileName + "] " + reason;
    }

}
