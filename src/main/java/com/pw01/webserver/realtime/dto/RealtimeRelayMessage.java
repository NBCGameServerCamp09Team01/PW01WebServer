package com.pw01.webserver.realtime.dto;

import java.util.List;

/**
 * 서버 사이 메시지(Redis Pub/Sub 채널에 싣는 JSON). 서버끼리만 쓰고 게임은 모른다.
 * 모양은 루트 docs/contracts/redis-keys.md "실시간 서버 사이 전달"이 기준이다.
 *
 * @param v          모양 판. 받는 서버는 모르는 판을 버린다(배포 중 옛·새 서버가 섞일 때)
 * @param kind       DELIVER(게임에 보내기) / SESSION_CHECK(그 계정 연결의 세션을 다시 확인) /
 *                   CONTENT_CHANGED(웨이브 정의가 바뀌었으니 다시 읽기, 서버끼리만, 판 12)
 * @param origin     보낸 서버 번호(RealtimeServerId). 자기가 보낸 것은 버린다
 * @param guildId    길드 채널일 때만. 채널 이름의 길드와 다르면 버린다
 * @param accountIds 받을 계정(길드·지정 채널). 전체 채널이면 null
 * @param message    게임에 갈 메시지 그대로(SESSION_CHECK·CONTENT_CHANGED이면 null). id가 모든 서버에서 같다
 */
public record RealtimeRelayMessage(int v, String kind, String origin, String guildId,
                                   List<String> accountIds, RealtimeMessage message) {

    public static final int VERSION = 1;
    public static final String DELIVER = "DELIVER";
    public static final String SESSION_CHECK = "SESSION_CHECK";
    public static final String CONTENT_CHANGED = "CONTENT_CHANGED";

    public static RealtimeRelayMessage deliver(String origin, String guildId, List<String> accountIds, RealtimeMessage message) {
        return new RealtimeRelayMessage(VERSION, DELIVER, origin, guildId, accountIds, message);
    }

    public static RealtimeRelayMessage sessionCheck(String origin, List<String> accountIds) {
        return new RealtimeRelayMessage(VERSION, SESSION_CHECK, origin, null, accountIds, null);
    }

    public static RealtimeRelayMessage contentChanged(String origin) {
        return new RealtimeRelayMessage(VERSION, CONTENT_CHANGED, origin, null, null, null);
    }

}
