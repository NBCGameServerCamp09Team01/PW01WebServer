package com.pw01.webserver.chat.entity;

import com.pw01.webserver.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 길드 채팅 한 줄(guild_chat_message, M23 채팅). 저장한 뒤 바꾸지 않는다.
 * 보낸 시각은 createdAt(JPA Auditing이 밀리초까지 잘라 채움) = ChatMessage.sentAt.
 */
@Entity
@Table(name = "guild_chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuildChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guild_chat_message_id")
    private Long id;

    @Column(nullable = false)
    private Long guildId;

    @Column(nullable = false)
    private Long senderAccountId;

    @Column(nullable = false, length = 20)
    private String senderNickname;

    @Column(nullable = false, length = 100)
    private String text;

    public static GuildChatMessage of(Long guildId, Long senderAccountId, String senderNickname, String text) {
        GuildChatMessage message = new GuildChatMessage();
        message.guildId = Objects.requireNonNull(guildId, "guildId");
        message.senderAccountId = Objects.requireNonNull(senderAccountId, "senderAccountId");
        message.senderNickname = Objects.requireNonNull(senderNickname, "senderNickname");
        message.text = Objects.requireNonNull(text, "text");
        return message;
    }

    /** 서버가 받은 시각. 저장(persist) 때 채워진다 */
    public Instant sentAt() {
        return getCreatedAt();
    }

}
