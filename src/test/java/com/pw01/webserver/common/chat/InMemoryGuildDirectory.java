package com.pw01.webserver.common.chat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시험용 가짜 GuildDirectory(메모리). 소셜 구현이 아직 없을 때 채팅 모듈이 이것으로 시험한다.
 * 한 사람 길드 하나 규칙만 지킨다(가입하면 이전 길드에서 빠짐).
 */
public class InMemoryGuildDirectory implements GuildDirectory {

    private final Map<Long, String> guildOfAccount = new ConcurrentHashMap<>();

    public InMemoryGuildDirectory join(String guildId, Long... accountIds) {
        for (Long accountId : accountIds) {
            guildOfAccount.put(accountId, guildId);
        }
        return this;
    }

    public void leave(Long accountId) {
        guildOfAccount.remove(accountId);
    }

    @Override
    public Optional<String> guildIdOf(Long accountId) {
        return Optional.ofNullable(guildOfAccount.get(accountId));
    }

    @Override
    public boolean isMember(String guildId, Long accountId) {
        return guildId.equals(guildOfAccount.get(accountId));
    }

    @Override
    public List<Long> memberIds(String guildId) {
        return guildOfAccount.entrySet().stream()
                .filter(entry -> entry.getValue().equals(guildId))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

}
