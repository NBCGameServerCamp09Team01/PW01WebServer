package com.pw01.webserver.guild.service;

import com.pw01.webserver.common.chat.GuildDirectory;
import com.pw01.webserver.guild.entity.AccountGuild;
import com.pw01.webserver.guild.repository.AccountGuildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 채팅(그리고 다른 기능)이 길드에 묻는 계약 GuildDirectory(common/chat)의 길드 구현.
 * account_guild 테이블에서 바로 읽는다(캐시 없음, redis-keys.md "길드" — 추방된 사람이 늦게 빠지는 일이 없게).
 * 계약의 길드 ID는 문자열이다. 숫자가 아닌 값은 예외 대신 "그런 길드 없음"으로 답한다(빈 값·false·빈 목록).
 * 부르는 쪽 트랜잭션이 있으면 그 안에서, 없으면 읽기 전용 트랜잭션에서 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuildDirectoryImpl implements GuildDirectory {

    private final AccountGuildRepository accountGuildRepository;

    @Override
    public Optional<String> guildIdOf(Long accountId) {
        return accountGuildRepository.findById(accountId)
                .map(AccountGuild::getGuildId)
                .map(String::valueOf);
    }

    @Override
    public boolean isMember(String guildId, Long accountId) {
        return GuildIds.parse(guildId)
                .flatMap(id -> accountGuildRepository.findById(accountId).map(person -> person.belongsTo(id)))
                .orElse(false);
    }

    @Override
    public List<Long> memberIds(String guildId) {
        return GuildIds.parse(guildId)
                .map(accountGuildRepository::findAccountIdsByGuildId)
                .orElse(List.of());
    }

}
