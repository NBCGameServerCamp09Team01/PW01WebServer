package com.pw01.webserver.chat.repository;

import com.pw01.webserver.chat.entity.GuildChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/** 길드 채팅 줄(M23 채팅) */
public interface GuildChatMessageRepository extends JpaRepository<GuildChatMessage, Long> {

    /**
     * 내역 한 묶음: 그 길드에서 번호가 beforeId보다 작고 since 이후에 받은 줄을 최신부터 limit개.
     * 인덱스 idx_guild_chat_message_guild(guild_id, guild_chat_message_id)를 탄다.
     */
    List<GuildChatMessage> findByGuildIdAndIdLessThanAndCreatedAtGreaterThanEqualOrderByIdDesc(
            Long guildId, Long beforeId, Instant since, Limit limit);

}
