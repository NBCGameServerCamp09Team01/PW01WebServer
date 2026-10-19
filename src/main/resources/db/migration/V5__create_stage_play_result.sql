-- S2: 스테이지 플레이(stage_play) · 결과 요약(stage_play_result) · 계정 변경 내역(account_ledger) · 계정 누적 통계(account_stat_total).
-- 시각은 UTC DATETIME(3)(BaseEntity).
-- 스테이지 플레이 ID는 서버가 발급하는 UUID(소문자·하이픈 36자). 이 ID를 담는 칼럼은 모두 CHAR(36) COLLATE utf8mb4_0900_bin이다
--   (V4 account_stage_progress.first_clear_run_id와 같은 정의. 정렬 규칙이 다르면 외래 키·조인이 실패한다).
-- stage_id는 V4와 같은 정의(VARCHAR(64) utf8mb4_0900_bin, master/stages.json 키).
-- 같은 플레이의 결과가 두 번 반영되지 않게 하는 마지막 방어는 stage_play_result의 기본 키(stage_play_id)다.

-- 계정이 스테이지 하나를 한 번 플레이하는 것(시작 → 결과·포기·만료). 계정당 진행 중은 하나다(in_progress_account_id 유일 제약).
-- 상태는 한 방향: IN_PROGRESS → CLEARED·FAILED(결과, 포기) 또는 EXPIRED(마감 지남, 다시 읽을 때 서버가 바꿈).
CREATE TABLE stage_play
(
    stage_play_id    CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '스테이지 플레이 ID(UUID 소문자·하이픈 36자). 서버가 발급',
    account_id       BIGINT      NOT NULL COMMENT 'account.account_id. 플레이 주인',
    stage_id         VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/stages.json 스테이지 키',
    wave_count       INT         NOT NULL COMMENT '시작 때의 서버 waveCount. 결과 검사 기준(유효 24시간 중 마스터가 바뀌어도 그대로)',
    difficulty       INT         NOT NULL DEFAULT 0 COMMENT '난이도. 0 = 난이도 조절 없음(기본). 레벨 스케일링 등은 나중',
    status           VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS, CLEARED, FAILED, EXPIRED',
    end_reason       VARCHAR(20) NULL COMMENT '끝난 이유: RESULT(결과 제출), ABANDONED(포기, 상태는 FAILED), EXPIRED. 진행 중이면 NULL',
    start_request_id CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '시작 요청의 requestId. 같은 값으로 다시 오면 같은 플레이를 돌려준다',
    started_at       DATETIME(3) NOT NULL COMMENT '시작 시각(UTC)',
    expires_at       DATETIME(3) NOT NULL COMMENT '결과를 낼 수 있는 마지막 시각(시작 + 24시간)',
    ended_at         DATETIME(3) NULL COMMENT '끝난 시각. 진행 중이면 NULL',
    in_progress_account_id BIGINT GENERATED ALWAYS AS (IF(status = 'IN_PROGRESS', account_id, NULL)) STORED
        COMMENT '진행 중이면 account_id, 아니면 NULL. 유일 제약으로 계정당 진행 중 하나(NULL은 여러 개 허용). 엔티티에는 없음',
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (stage_play_id),
    CONSTRAINT uk_stage_play_account_start_request UNIQUE (account_id, start_request_id),
    CONSTRAINT uk_stage_play_one_in_progress UNIQUE (in_progress_account_id),
    CONSTRAINT fk_stage_play_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_stage_play_id CHECK (REGEXP_LIKE(stage_play_id, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT ck_stage_play_wave_count CHECK (wave_count >= 1),
    CONSTRAINT ck_stage_play_difficulty CHECK (difficulty >= 0),
    CONSTRAINT ck_stage_play_status CHECK (status IN ('IN_PROGRESS', 'CLEARED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_stage_play_end_reason CHECK (end_reason IS NULL OR end_reason IN ('RESULT', 'ABANDONED', 'EXPIRED')),
    CONSTRAINT ck_stage_play_ended CHECK (
        (status = 'IN_PROGRESS' AND end_reason IS NULL AND ended_at IS NULL)
        OR (status <> 'IN_PROGRESS' AND end_reason IS NOT NULL AND ended_at IS NOT NULL)),
    INDEX ix_stage_play_account_started (account_id, started_at),
    INDEX ix_stage_play_status_started (status, started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stage_play_result
(
    stage_play_id      CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'stage_play.stage_play_id(1:1). 기본 키 = 같은 플레이 두 번 반영 막기',
    account_id         BIGINT      NOT NULL COMMENT 'account.account_id(조회용, stage_play와 같은 값)',
    stage_id           VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '플레이의 스테이지 키(랭킹·난이도가 같은 칼럼을 씀)',
    difficulty         INT         NOT NULL,
    cleared            BOOLEAN     NOT NULL,
    reached_wave       INT         NOT NULL COMMENT '도달한 웨이브(0부터)',
    wave_count         INT         NOT NULL COMMENT '플레이의 웨이브 수(stage_play.wave_count)',
    reported_wave_count INT        NOT NULL COMMENT '게임이 보낸 전체 웨이브 수(검사에 쓰지 않음, 어긋남 추적용)',
    play_time_ms       BIGINT      NOT NULL COMMENT '플레이 시간(밀리초)',
    earned_gold        INT         NOT NULL,
    kill_count         INT         NOT NULL,
    exp_gained         INT         NOT NULL COMMENT '서버가 계산한 경험치',
    level_before       INT         NOT NULL,
    level_after        INT         NOT NULL,
    stat_points_gained INT         NOT NULL,
    request_id         CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '처음 저장한 결과 요청의 requestId(추적용)',
    created_at         DATETIME(3) NOT NULL COMMENT '저장 시각 = 응답의 submittedAt',
    updated_at         DATETIME(3) NOT NULL,
    PRIMARY KEY (stage_play_id),
    CONSTRAINT fk_stage_play_result_play FOREIGN KEY (stage_play_id) REFERENCES stage_play (stage_play_id),
    CONSTRAINT fk_stage_play_result_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_stage_play_result_numbers CHECK (reached_wave >= 0 AND wave_count >= 1 AND play_time_ms >= 0
        AND earned_gold >= 0 AND kill_count >= 0 AND exp_gained >= 0 AND stat_points_gained >= 0),
    INDEX ix_stage_play_result_account_created (account_id, created_at, stage_play_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE account_ledger
(
    ledger_id   BIGINT      NOT NULL AUTO_INCREMENT,
    account_id  BIGINT      NOT NULL COMMENT 'account.account_id',
    kind        VARCHAR(30) NOT NULL COMMENT '바뀐 이유. STAGE_REWARD(결과 보상). S3·S4가 종류를 더함',
    target      VARCHAR(100) COLLATE utf8mb4_0900_bin NULL COMMENT '대상(투자한 스탯·해금한 스킬 태그). 보상은 NULL',
    exp_delta   BIGINT      NOT NULL COMMENT '총 경험치 변화',
    point_delta INT         NOT NULL COMMENT '스탯 포인트 변화(쓰면 음수)',
    source_type VARCHAR(20) NOT NULL COMMENT '출처 종류. STAGE_PLAY(스테이지 플레이)',
    source_id   VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '출처 ID. STAGE_PLAY면 스테이지 플레이 ID',
    request_id  CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '바꾼 요청의 requestId',
    created_at  DATETIME(3) NOT NULL,
    updated_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (ledger_id),
    CONSTRAINT uk_account_ledger_source UNIQUE (account_id, source_type, source_id, kind),
    CONSTRAINT fk_account_ledger_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    INDEX ix_account_ledger_account_created (account_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE account_stat_total
(
    account_id BIGINT       NOT NULL COMMENT 'account.account_id',
    stat_key   VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '누적 통계 키(stage_play.played, stage_play.cleared, stage_play.kills, stage_play.gold). S4 해금 조건 재료',
    total      BIGINT       NOT NULL COMMENT '결과마다 더한 합계',
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (account_id, stat_key),
    CONSTRAINT fk_account_stat_total_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_account_stat_total_total CHECK (total >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
