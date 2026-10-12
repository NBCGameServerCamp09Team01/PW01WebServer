-- 번호는 PR 직전에 dev의 마지막 번호 + 1로 맞춘다(docs/migrations.md).
-- S3 스탯 투자: 계정마다 스탯별 투자 포인트(지시서 1-1a, 계정 + 종류별 자식 표). 명세는 루트 docs/contracts/stat-api.md.
-- 락은 진행 행(account_progress.version)에만 둔다. 이 표만 바뀌어도 투자 서비스가 진행 행 version을 올린다.
-- 최대 포인트는 마스터 데이터(master/stats.json)라 바뀔 수 있어 CHECK로 두지 않는다(서비스가 판정). 0 아래만 DB가 막는다.
-- 내려서 0이 돼도 행은 지우지 않는다(응답·계정 값에서는 0인 스탯을 뺀다).
-- stat_key는 UE 게임플레이 태그 이름이고 대소문자를 가린다(utf8mb4_0900_bin).

CREATE TABLE account_stat
(
    account_id BIGINT       NOT NULL COMMENT 'account.account_id',
    stat_key   VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/stats.json 스탯 태그 = UE 게임플레이 태그(예: Account.Stat.MaxHealth)',
    points     INT          NOT NULL COMMENT '투자 포인트(0 이상, 최대는 마스터 데이터)',
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (account_id, stat_key),
    CONSTRAINT fk_account_stat_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_account_stat_points CHECK (points >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
