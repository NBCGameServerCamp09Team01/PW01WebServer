package com.pw01.webserver.stage.repository;

/**
 * 출처에서 읽은 웨이브 정의 파일 하나.
 *
 * @param content 파일 바이트(UTF-8 JSON)
 * @param etag    S3 객체의 ETag(내용이 바뀌었는지 비교). 리소스 파일이면 null
 */
public record StageWaveFile(byte[] content, String etag) {
}
