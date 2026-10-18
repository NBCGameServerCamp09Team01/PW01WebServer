package com.pw01.webserver.realtime.event;

/**
 * 다른 서버가 웨이브 정의를 바꿨다(서버 사이 전달 kind CONTENT_CHANGED, 판 12). 정의 다시 읽기 기능이 받는다.
 * 실시간 받기 스레드(하나)에서 바로 불리므로 받는 쪽은 S3 읽기 같은 오래 걸리는 일을 자기 스레드로 넘긴다.
 *
 * @param origin 바꾼 서버 번호
 */
public record ServerContentChangedEvent(String origin) {
}
