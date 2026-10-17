-- 번호는 PR 직전에 dev의 마지막 번호 + 1로 맞춘다(docs/migrations.md). V11은 채팅 담당 예약.
-- M23 길드(소셜). 명세는 루트 docs/contracts/guild-api.md, 설계는 docs/decisions/0003-guild-concurrency.md(W8).
-- 표 넷: guild(길드), account_guild(계정마다 소속 한 줄 = "사람 줄"), guild_join_request(대기 중 신청), guild_counter(서버 길드 수).
-- 동시성: guild·account_guild·guild_counter는 version(@Version, 낙관적 락). 해체만 guild 줄을 FOR UPDATE로 먼저 잡는다.
--   잠금 순서는 길드 수 줄 → 길드 줄 → 사람 줄 → 신청 줄. 아래 유일·외래 키·CHECK는 서비스 판정 뒤의 마지막 방어다.
-- 외래 키에는 참조 동작(ON DELETE·ON UPDATE)을 두지 않는다. MySQL은 참조 동작에 쓰인 칼럼을 CHECK에 쓰지 못하고(account_guild.guild_id),
--   해체는 서비스가 사람 줄 소속을 먼저 비우고 신청을 지운 뒤 길드를 지운다.
-- 길드 채팅 표(V11)는 guild에 외래 키를 걸지 않는다(채팅이 해체 이벤트를 받아 자기 줄을 지움).

CREATE TABLE guild
(
    guild_id          BIGINT      NOT NULL AUTO_INCREMENT,
    name              VARCHAR(12) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '길드 이름. 한글·영문·숫자 2~12자, 가운데 공백 하나씩, 대소문자 가림, 유일',
    leader_account_id BIGINT      NOT NULL COMMENT '길드장 account.account_id. 길드장 넘기기는 없음',
    member_count      INT         NOT NULL COMMENT '길드원 수(길드장 포함). account_guild의 실제 수와 같게 같은 트랜잭션에서 고침',
    max_members       INT         NOT NULL COMMENT '정원. 만들 때 설정 값(pw01.guild.max-members)을 적음',
    version           BIGINT      NOT NULL COMMENT '낙관적 락 번호(@Version). 인원이 바뀔 때마다 오름',
    created_at        DATETIME(3) NOT NULL,
    updated_at        DATETIME(3) NOT NULL,
    PRIMARY KEY (guild_id),
    CONSTRAINT uk_guild_name UNIQUE (name),
    CONSTRAINT fk_guild_leader_account FOREIGN KEY (leader_account_id) REFERENCES account (account_id),
    CONSTRAINT ck_guild_name CHECK (REGEXP_LIKE(name, '^[가-힣A-Za-z0-9][가-힣A-Za-z0-9 ]{0,10}[가-힣A-Za-z0-9]$', 'c')
        AND name NOT LIKE '%  %'),
    CONSTRAINT ck_guild_max_members CHECK (max_members >= 1),
    CONSTRAINT ck_guild_member_count CHECK (member_count BETWEEN 1 AND max_members)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 계정마다 한 줄(기본 키 = 계정). 가입 때 무소속 줄을 만들고, 기존 계정은 아래 INSERT가 채운다.
-- 무소속이면 guild_id·role·joined_at 셋 다 NULL, 소속이면 셋 다 값(ck_account_guild_membership).
CREATE TABLE account_guild
(
    account_id BIGINT      NOT NULL COMMENT 'account.account_id와 같은 값(1:1)',
    guild_id   BIGINT      NULL COMMENT '소속 길드. NULL = 무소속',
    role       VARCHAR(10) NULL COMMENT 'LEADER · MEMBER. 무소속이면 NULL',
    joined_at  DATETIME(3) NULL COMMENT '들어온 시각(UTC). 무소속이면 NULL',
    version    BIGINT      NOT NULL COMMENT '낙관적 락 번호(@Version). 신청 때는 먼저 올려 같은 사람의 동시 신청을 줄 세움',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    INDEX idx_account_guild_guild_joined (guild_id, joined_at),
    CONSTRAINT fk_account_guild_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_account_guild_guild FOREIGN KEY (guild_id) REFERENCES guild (guild_id),
    CONSTRAINT ck_account_guild_role CHECK (role IN ('LEADER', 'MEMBER')),
    CONSTRAINT ck_account_guild_membership CHECK (
        (guild_id IS NULL AND role IS NULL AND joined_at IS NULL)
            OR (guild_id IS NOT NULL AND role IS NOT NULL AND joined_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 대기 중 신청. 수락·거절·취소·해체되면 지운다(기록을 남기지 않음). 유효 기간(3일)은 created_at + 설정 값으로 조회 때 거른다.
-- kind는 초대 자리: 지금은 APPLICATION(신청)만. 초대를 넣을 때 CHECK에 INVITATION을 더하는 마이그레이션 하나.
-- 유일 (account_id, guild_id)는 같은 길드와 대기 중인 관계를 하나로 막는다(신청·초대 함께).
CREATE TABLE guild_join_request
(
    guild_join_request_id BIGINT      NOT NULL AUTO_INCREMENT,
    guild_id              BIGINT      NOT NULL COMMENT 'guild.guild_id',
    account_id            BIGINT      NOT NULL COMMENT '신청한 account.account_id',
    kind                  VARCHAR(20) NOT NULL COMMENT 'APPLICATION(신청). 초대 자리',
    created_at            DATETIME(3) NOT NULL COMMENT '신청 시각(UTC). 유효 기간의 시작',
    updated_at            DATETIME(3) NOT NULL,
    PRIMARY KEY (guild_join_request_id),
    CONSTRAINT uk_guild_join_request_account_guild UNIQUE (account_id, guild_id),
    INDEX idx_guild_join_request_guild (guild_id, guild_join_request_id),
    CONSTRAINT fk_guild_join_request_guild FOREIGN KEY (guild_id) REFERENCES guild (guild_id),
    CONSTRAINT fk_guild_join_request_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_guild_join_request_kind CHECK (kind IN ('APPLICATION'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 서버 길드 수. 줄은 하나(counter_id = 1). 상한(기본 5)은 설정 값이라 CHECK로 두지 않는다(서비스가 판정).
CREATE TABLE guild_counter
(
    counter_id  INT         NOT NULL COMMENT '늘 1',
    guild_count INT         NOT NULL COMMENT '지금 길드 수. 만들기 +1, 해체 -1',
    version     BIGINT      NOT NULL COMMENT '낙관적 락 번호(@Version). 동시 만들기를 줄 세움',
    created_at  DATETIME(3) NOT NULL,
    updated_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (counter_id),
    CONSTRAINT ck_guild_counter_single CHECK (counter_id = 1),
    CONSTRAINT ck_guild_counter_count CHECK (guild_count >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO guild_counter (counter_id, guild_count, version, created_at, updated_at)
VALUES (1, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- 기존 계정마다 무소속 줄. 이 마이그레이션 뒤의 가입은 가입 이벤트가 만들고(W2),
-- 섞여 도는 배포에서 옛 서버가 받은 가입처럼 줄이 없으면 길드 기능이 그 자리에서 만든다(insertIfAbsent).
INSERT INTO account_guild (account_id, guild_id, role, joined_at, version, created_at, updated_at)
SELECT account_id, NULL, NULL, NULL, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM account;
