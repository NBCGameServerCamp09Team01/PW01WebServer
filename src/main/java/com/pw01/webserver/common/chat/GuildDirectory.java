package com.pw01.webserver.common.chat;

import java.util.List;
import java.util.Optional;

/**
 * 채팅(그리고 다른 기능)이 길드에 묻는 것. 소셜(길드) 모듈이 구현한다(M23 뼈대 약속).
 * 이 인터페이스만 보면 길드 안(표·캐시)을 몰라도 된다. 구현이 없는 동안 채팅은 시험용 가짜 구현으로 시험한다.
 */
public interface GuildDirectory {

    /** 이 계정의 길드 ID. 길드가 없으면 빈 값(한 사람 길드 하나, 미소속 가능) */
    Optional<String> guildIdOf(Long accountId);

    /** 이 계정이 이 길드의 길드원인지 */
    boolean isMember(String guildId, Long accountId);

    /** 이 길드의 길드원 전부(보낸 사람 포함). RealtimeNotifier.sendToGuild의 대상으로 쓴다 */
    List<Long> memberIds(String guildId);

}
