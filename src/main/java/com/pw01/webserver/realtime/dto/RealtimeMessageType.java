package com.pw01.webserver.realtime.dto;

/**
 * 서버 → 게임 메시지 종류. 루트 docs/contracts/realtime-api.md "메시지 종류" 표와 같다.
 * 새 종류는 그 표에 먼저 적고(명세 v1.x) 여기에 더한다. 게임은 모르는 종류를 무시한다.
 */
public enum RealtimeMessageType {

    /** 같은 계정이 다른 곳에서 로그인했다. 보낸 뒤 종료 코드 4001로 닫는다. data는 {} */
    SESSION_REPLACED,

    /** 예약: 계정 진행이 바뀌었다. v1에서는 보내지 않는다(보내는 경우가 생기면 명세 v1.1에 적고 쓴다) */
    ACCOUNT_CHANGED

}
