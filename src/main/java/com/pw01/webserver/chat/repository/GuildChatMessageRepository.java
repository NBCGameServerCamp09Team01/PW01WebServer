package com.pw01.webserver.chat.repository;

import com.pw01.webserver.chat.entity.GuildChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 해체된 길드의 줄을 limit개까지 지운다(짧은 트랜잭션을 0줄이 될 때까지 반복하려고 LIMIT). 지운 줄 수.
     * 인덱스 idx_guild_chat_message_guild의 앞 칸 guild_id를 탄다.
     */
    @Modifying
    @Query(value = "DELETE FROM guild_chat_message WHERE guild_id = :guildId LIMIT :limit", nativeQuery = true)
    int deleteBatchOfGuild(@Param("guildId") Long guildId, @Param("limit") int limit);

    /** 보관 기간이 지난 줄(created_at < before)을 limit개까지 지운다. 지운 줄 수. 인덱스 idx_guild_chat_message_created를 탄다 */
    @Modifying
    @Query(value = "DELETE FROM guild_chat_message WHERE created_at < :before LIMIT :limit", nativeQuery = true)
    int deleteBatchCreatedBefore(@Param("before") Instant before, @Param("limit") int limit);

}
