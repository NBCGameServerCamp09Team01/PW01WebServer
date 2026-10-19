-- 첫 흐름(S1) 계정 테이블: account(로그인 정보·프로필) + account_progress(게임 진행), 같은 계정 ID로 1:1.
-- 시각은 UTC DATETIME(3)(BaseEntity, V1과 같음). 테이블 정렬 규칙은 V1과 같은 utf8mb4_0900_ai_ci(대소문자를 같은 글자로 봄).
-- login_id만 utf8mb4_0900_bin: 아이디는 대소문자를 구분한다(Warrior01과 warrior01은 다른 아이디, 유일·로그인 조회 모두).
-- 닉네임은 테이블 정렬 규칙을 따라 대소문자만 다르면 같은 닉네임이다.
-- 형식 규칙(CHECK)은 요청 검증과 같은 규칙의 마지막 방어다. 규칙을 바꾸면 이 파일을 고치지 않고 새 마이그레이션으로 CHECK를 바꾼다.

CREATE TABLE account
(
    account_id    BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(20) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '로그인 아이디. 영문 대소문자·숫자 4~20자, 대소문자 구분, 입력 그대로 저장',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 해시. 원문은 저장하지 않음',
    nickname      VARCHAR(20)  NOT NULL COMMENT '화면 이름. 한글·영문·숫자 2~20자, 가운데 공백 가능, 유일',
    email         VARCHAR(254) NULL COMMENT '선택 칸. 유일 아님',
    status        VARCHAR(20)  NOT NULL COMMENT 'ACTIVE, SUSPENDED(제재), DELETION_REQUESTED(삭제 요청)',
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT uk_account_login_id UNIQUE (login_id),
    CONSTRAINT uk_account_nickname UNIQUE (nickname),
    CONSTRAINT ck_account_login_id CHECK (REGEXP_LIKE(login_id, '^[A-Za-z0-9]{4,20}$', 'c')),
    CONSTRAINT ck_account_nickname CHECK (REGEXP_LIKE(nickname, '^[가-힣A-Za-z0-9][가-힣A-Za-z0-9 ]{0,18}[가-힣A-Za-z0-9]$', 'c')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE account_progress
(
    account_id       BIGINT      NOT NULL COMMENT 'account.account_id와 같은 값(1:1)',
    level            INT         NOT NULL COMMENT '계정 레벨. 1부터',
    experience       INT         NOT NULL COMMENT '지금 레벨 안의 경험치. API·UE에 보내는 값',
    total_experience BIGINT      NOT NULL COMMENT '누적 총 경험치. 레벨 계산의 원본, API에는 보내지 않음',
    stat_points      INT         NOT NULL COMMENT '남은 스탯 포인트',
    version          BIGINT      NOT NULL COMMENT '낙관적 락 번호(@Version). 응답의 version',
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_progress_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_account_progress_level CHECK (level >= 1),
    CONSTRAINT ck_account_progress_experience CHECK (experience >= 0),
    CONSTRAINT ck_account_progress_total_experience CHECK (total_experience >= 0),
    CONSTRAINT ck_account_progress_stat_points CHECK (stat_points >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
