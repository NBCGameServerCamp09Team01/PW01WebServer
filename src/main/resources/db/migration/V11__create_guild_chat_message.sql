-- 번호는 PR 직전에 dev의 마지막 번호 + 1로 맞춘다(docs/migrations.md). V10은 길드(Sang).
-- M23 채팅: 길드 채팅 한 줄. 명세는 루트 docs/contracts/chat-api.md, ERD는 docs/erd/chat.md.
-- 전체 채팅은 저장하지 않는다(NoStoreGlobalChatHistory). 길드 채팅만 3일 보관(pw01.chat.guild.retention).
-- 외래 키를 걸지 않는다: guild_id는 해체 때 채팅이 GuildDisbandedEvent를 받아 지우고(길드가 채팅 표를 몰라도 됨),
--   sender_account_id는 보낸 때 닉네임을 같이 복사해 두므로 계정 표와 묶지 않는다.
-- 내역은 (guild_id, guild_chat_message_id) 순으로 최신부터 커서 읽기, 보관 지우기는 created_at으로 찾는다.

CREATE TABLE guild_chat_message
(
    guild_chat_message_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'messageId·커서(길드 안 순서 = 번호 순)',
    guild_id              BIGINT       NOT NULL COMMENT 'guild.guild_id(외래 키 없음 — 해체 이벤트로 지움)',
    sender_account_id     BIGINT       NOT NULL COMMENT '보낸 account.account_id(외래 키 없음)',
    sender_nickname       VARCHAR(20)  NOT NULL COMMENT '보낸 때 닉네임 복사(account.nickname과 같은 길이)',
    text                  VARCHAR(100) NOT NULL COMMENT '채팅 글. 코드 포인트 100자(pw01.chat.max-length)',
    created_at            DATETIME(3)  NOT NULL COMMENT '서버가 받은 시각(UTC) = sentAt',
    updated_at            DATETIME(3)  NOT NULL,
    PRIMARY KEY (guild_chat_message_id),
    INDEX idx_guild_chat_message_guild (guild_id, guild_chat_message_id),
    INDEX idx_guild_chat_message_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
