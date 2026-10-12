-- 랭킹(M7): 루트 docs/contracts/ranking-api.md v1, redis-keys.md v1.3 랭킹 절.
-- 번호는 PR 직전에 dev의 마지막 번호 + 1로 맞춘다(S3 V6과 같은 기간, docs/migrations.md).
-- 랭킹의 원천(영구)은 이 표 다섯이고, Redis 보드는 여기서 언제든 다시 채우는 사본이다.
--   ranking_clear_log       클리어마다 한 줄(덧붙이기만). clear_seq = 기록 순번("먼저 기록된 것"), 주간 보드의 원천
--   stage_best_record       계정 × 스테이지 × 난이도 최고 점수(오르면 통째로 덮어씀)
--   region_best_score       계정 × 지역 × 난이도 점수(스테이지 최고 점수의 합)
--   total_best_score        계정 × 난이도 전체 총합(모든 스테이지 최고 점수의 합)
--   account_clear_progress  계정 진행(클리어한 스테이지 수, 난이도와 상관없음)
-- 순서는 모든 보드가 같다: 점수 높은 쪽 → 달성 시각 이른 쪽 → clear_seq 작은 쪽. 순위 인덱스는 이 순서 그대로다.
-- 결과 표(stage_play_result, S2)에는 외래 키를 걸지 않는다(V4 first_clear_run_id와 같은 이유: S2와 병합 순서에 묶이지 않게, 랭킹 단독 시험).
-- 시각은 UTC DATETIME(3). 달성 시각은 결과 저장 시각(stage_play_result.created_at)과 같은 값이다.
-- stage_id·region_id·stage_play_id 정의는 V4·V5와 같다(utf8mb4_0900_bin, 다르면 조인이 실패한다).
-- 랭킹이 생기기 전 결과는 채우지 않는다(시연은 빈 인프라에서 시작).

CREATE TABLE ranking_clear_log
(
    clear_seq           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '기록 순번. 동점(같은 점수·같은 달성 밀리초)이면 작은 쪽이 위',
    stage_play_id       CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '스테이지 플레이 ID(결과 하나에 한 줄). 결과 표에 외래 키 없음',
    account_id          BIGINT      NOT NULL COMMENT 'account.account_id',
    stage_id            VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/stages.json 스테이지 키',
    region_id           VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '그때 그 스테이지가 속한 지역 키(주간 지역 보드)',
    difficulty          INT         NOT NULL COMMENT '난이도(지금 0만)',
    score               INT         NOT NULL COMMENT '스테이지 점수(ranking-api.md "점수", 기록할 때 계산)',
    play_time_ms        BIGINT      NOT NULL COMMENT '클리어 시간(stage_play_result.play_time_ms)',
    recommended_time_ms BIGINT      NOT NULL COMMENT '그때의 권장 클리어 시간(점수 계산 근거)',
    achieved_at         DATETIME(3) NOT NULL COMMENT '달성 시각 = 결과 저장 시각',
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    PRIMARY KEY (clear_seq),
    CONSTRAINT uk_ranking_clear_log_play UNIQUE (stage_play_id),
    CONSTRAINT fk_ranking_clear_log_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_ranking_clear_log_numbers CHECK (difficulty >= 0 AND score >= 0 AND play_time_ms >= 0
        AND recommended_time_ms >= 1),
    -- 주간 스테이지·지역 보드(그 주만 걸러 계정별 최고)
    INDEX ix_ranking_clear_log_stage_week (stage_id, difficulty, achieved_at),
    INDEX ix_ranking_clear_log_region_week (region_id, difficulty, achieved_at),
    INDEX ix_ranking_clear_log_week (difficulty, achieved_at),
    -- 처음 클리어 판단·진행 다시 계산(계정의 클리어한 스테이지)
    INDEX ix_ranking_clear_log_account_stage (account_id, stage_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stage_best_record
(
    stage_id      VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/stages.json 스테이지 키',
    difficulty    INT         NOT NULL COMMENT '난이도(지금 0만)',
    account_id    BIGINT      NOT NULL COMMENT 'account.account_id',
    score         INT         NOT NULL COMMENT '최고 스테이지 점수',
    play_time_ms  BIGINT      NOT NULL COMMENT '그 최고 점수의 클리어 시간',
    achieved_at   DATETIME(3) NOT NULL COMMENT '그 최고 점수의 달성 시각',
    clear_seq     BIGINT      NOT NULL COMMENT '그 최고 점수의 기록 순번(ranking_clear_log)',
    stage_play_id CHAR(36)    COLLATE utf8mb4_0900_bin NOT NULL COMMENT '그 최고 점수의 스테이지 플레이 ID(추적)',
    clear_count   INT         NOT NULL COMMENT '이 스테이지·난이도 클리어 횟수(덮어쓰지 않아도 늘어남)',
    created_at    DATETIME(3) NOT NULL,
    updated_at    DATETIME(3) NOT NULL,
    PRIMARY KEY (stage_id, difficulty, account_id),
    CONSTRAINT fk_stage_best_record_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_stage_best_record_clear FOREIGN KEY (clear_seq) REFERENCES ranking_clear_log (clear_seq),
    CONSTRAINT ck_stage_best_record_numbers CHECK (difficulty >= 0 AND score >= 0 AND play_time_ms >= 0
        AND clear_count >= 1),
    -- 순위 목록·커서·내 순위를 이 인덱스 하나로(점수 내림차순 → 달성 → 순번)
    INDEX ix_stage_best_record_rank (stage_id, difficulty, score DESC, achieved_at, clear_seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE region_best_score
(
    region_id           VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/stages.json 지역 키',
    difficulty          INT         NOT NULL COMMENT '난이도(지금 0만)',
    account_id          BIGINT      NOT NULL COMMENT 'account.account_id',
    total_score         INT         NOT NULL COMMENT '그 지역 스테이지마다 최고 점수의 합',
    cleared_stage_count INT         NOT NULL COMMENT '그 지역에서 클리어한 스테이지 수(이 난이도)',
    achieved_at         DATETIME(3) NOT NULL COMMENT '합이 마지막으로 오른 기록의 달성 시각',
    clear_seq           BIGINT      NOT NULL COMMENT '합이 마지막으로 오른 기록의 순번',
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    PRIMARY KEY (region_id, difficulty, account_id),
    CONSTRAINT fk_region_best_score_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_region_best_score_clear FOREIGN KEY (clear_seq) REFERENCES ranking_clear_log (clear_seq),
    CONSTRAINT ck_region_best_score_numbers CHECK (difficulty >= 0 AND total_score >= 0 AND cleared_stage_count >= 1),
    INDEX ix_region_best_score_rank (region_id, difficulty, total_score DESC, achieved_at, clear_seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE total_best_score
(
    difficulty          INT         NOT NULL COMMENT '난이도(지금 0만)',
    account_id          BIGINT      NOT NULL COMMENT 'account.account_id',
    total_score         INT         NOT NULL COMMENT '모든 스테이지마다 최고 점수의 합(모든 지역)',
    cleared_stage_count INT         NOT NULL COMMENT '클리어한 스테이지 수(이 난이도)',
    achieved_at         DATETIME(3) NOT NULL COMMENT '합이 마지막으로 오른 기록의 달성 시각',
    clear_seq           BIGINT      NOT NULL COMMENT '합이 마지막으로 오른 기록의 순번',
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    PRIMARY KEY (difficulty, account_id),
    CONSTRAINT fk_total_best_score_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_total_best_score_clear FOREIGN KEY (clear_seq) REFERENCES ranking_clear_log (clear_seq),
    CONSTRAINT ck_total_best_score_numbers CHECK (difficulty >= 0 AND total_score >= 0 AND cleared_stage_count >= 1),
    INDEX ix_total_best_score_rank (difficulty, total_score DESC, achieved_at, clear_seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE account_clear_progress
(
    account_id          BIGINT      NOT NULL COMMENT 'account.account_id',
    cleared_stage_count INT         NOT NULL COMMENT '클리어한 스테이지 수(난이도와 상관없음)',
    furthest_stage_id   VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '클리어한 스테이지 중 가장 뒤(지역 order → 스테이지 order)',
    achieved_at         DATETIME(3) NOT NULL COMMENT '그 수에 닿은(새 스테이지를 처음 클리어한) 기록의 달성 시각',
    clear_seq           BIGINT      NOT NULL COMMENT '그 기록의 순번',
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_clear_progress_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_account_clear_progress_clear FOREIGN KEY (clear_seq) REFERENCES ranking_clear_log (clear_seq),
    CONSTRAINT ck_account_clear_progress_count CHECK (cleared_stage_count >= 1),
    INDEX ix_account_clear_progress_rank (cleared_stage_count DESC, achieved_at, clear_seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
