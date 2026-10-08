-- S2 결과 제출 + 보상: 판(run) · 결과 요약(run_result) · 계정 변경 내역(account_ledger) · 계정 누적 통계(account_stat_total).
-- 명세: 루트 docs/contracts/result-api.md v1. 시각은 UTC DATETIME(3)(BaseEntity).
-- 판 번호는 서버가 발급하는 UUID(소문자·하이픈 36자). 판 번호를 담는 칼럼은 모두 CHAR(36) COLLATE utf8mb4_0900_bin이다
--   (V4 account_stage_progress.first_clear_run_id와 같은 정의. 정렬 규칙이 다르면 외래 키·조인이 실패한다).
-- stage_id는 V4와 같은 정의(VARCHAR(64) utf8mb4_0900_bin, master/stages.json 키).
-- run의 run_id·stage_id·wave_count 줄과 CHECK는 d(스테이지 담당)가 쓴 줄이다(stage-lines-for-c-1008.md 1장).
-- 같은 판의 결과가 두 번 반영되지 않게 하는 마지막 방어는 run_result의 기본 키(run_id)다.

CREATE TABLE run
(
    run_id           CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '판 번호(UUID 소문자·하이픈 36자). 서버가 발급',
    account_id       BIGINT      NOT NULL COMMENT 'account.account_id',
    stage_id         VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/stages.json 스테이지 키',
    wave_count       INT         NOT NULL COMMENT '판 시작 때의 서버 waveCount. 결과 검사 기준(판 유효 24시간 중 마스터가 바뀌어도 그대로)',
    difficulty       INT         NOT NULL COMMENT '난이도. 지금은 0만',
    status           VARCHAR(20) NOT NULL COMMENT 'ISSUED(발급됨), FINISHED(결과 받음)',
    start_request_id CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '판 시작 요청의 requestId. 같은 값으로 다시 오면 같은 판을 돌려준다',
    issued_at        DATETIME(3) NOT NULL COMMENT '발급 시각(UTC)',
    expires_at       DATETIME(3) NOT NULL COMMENT '결과를 낼 수 있는 마지막 시각(발급 + 24시간)',
    finished_at      DATETIME(3) NULL COMMENT '결과를 받은 시각',
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (run_id),
    CONSTRAINT uk_run_account_start_request UNIQUE (account_id, start_request_id),
    CONSTRAINT fk_run_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_run_run_id CHECK (REGEXP_LIKE(run_id, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT ck_run_wave_count CHECK (wave_count >= 1),
    CONSTRAINT ck_run_difficulty CHECK (difficulty >= 0),
    CONSTRAINT ck_run_status CHECK (status IN ('ISSUED', 'FINISHED')),
    INDEX ix_run_account_issued (account_id, issued_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE run_result
(
    run_id             CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'run.run_id(1:1). 기본 키 = 같은 판 두 번 반영 막기',
    account_id         BIGINT      NOT NULL COMMENT 'account.account_id(조회용, run과 같은 값)',
    stage_id           VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '판의 스테이지 키(랭킹·난이도가 같은 칼럼을 씀)',
    difficulty         INT         NOT NULL,
    cleared            BOOLEAN     NOT NULL,
    reached_wave       INT         NOT NULL COMMENT '도달한 웨이브(0부터)',
    wave_count         INT         NOT NULL COMMENT '판의 웨이브 수(run.wave_count)',
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
    PRIMARY KEY (run_id),
    CONSTRAINT fk_run_result_run FOREIGN KEY (run_id) REFERENCES run (run_id),
    CONSTRAINT fk_run_result_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_run_result_numbers CHECK (reached_wave >= 0 AND wave_count >= 1 AND play_time_ms >= 0
        AND earned_gold >= 0 AND kill_count >= 0 AND exp_gained >= 0 AND stat_points_gained >= 0),
    INDEX ix_run_result_account_created (account_id, created_at, run_id)
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
    source_type VARCHAR(20) NOT NULL COMMENT '출처 종류. RUN(판)',
    source_id   VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '출처 ID. RUN이면 판 번호',
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
    stat_key   VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '누적 통계 키(run.played, run.cleared, run.kills, run.gold). S4 해금 조건 재료',
    total      BIGINT       NOT NULL COMMENT '결과마다 더한 합계',
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (account_id, stat_key),
    CONSTRAINT fk_account_stat_total_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_account_stat_total_total CHECK (total >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
