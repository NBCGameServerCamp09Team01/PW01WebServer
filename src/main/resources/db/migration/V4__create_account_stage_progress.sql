-- [안건7] 번호는 PR 직전에 dev의 마지막 번호 + 1로 맞춘다(V3은 c의 예시 삭제, 순서 안은 회의에서 정함).
-- 계정마다 클리어한 스테이지(행이 있으면 클리어). 열림·잠김은 마스터 데이터(master/stages.json)의 requires로 서버가 계산하고 표에 두지 않는다.
-- 행은 넣기만 하고 고치지 않는다(다시 클리어해도 그대로). 같은 계정·스테이지는 한 행(유일 제약).
-- 판 표에는 외래 키를 걸지 않는다(S2와 병합 순서에 묶이지 않게). 판 번호는 그냥 칼럼으로 남긴다.
-- stage_id는 UE FName과 같은 문자열이고 대소문자를 가린다(utf8mb4_0900_bin). 모양 규칙은 마스터 데이터 검사와 같다.
-- first_clear_run_id는 S2 판 번호(UUID, 소문자·하이픈 36자)와 같은 정의다(CHAR(36), utf8mb4_0900_bin). 정렬 규칙이 같아야 판 표와 조인할 수 있다.
-- 난이도를 더할 때는 이 파일을 고치지 않고 새 마이그레이션으로 칼럼과 유일 제약(계정, 스테이지, 난이도)을 바꾼다.

CREATE TABLE account_stage_progress
(
    account_stage_progress_id BIGINT      NOT NULL AUTO_INCREMENT,
    account_id                BIGINT      NOT NULL COMMENT 'account.account_id',
    stage_id                  VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/stages.json 스테이지 키 = UE FName. 바꾸지 않음',
    first_cleared_at          DATETIME(3) NOT NULL COMMENT '서버가 처음 클리어 결과를 받아들인 시각(UTC). 재전송이면 플레이 시각보다 늦을 수 있음',
    first_clear_run_id        CHAR(36)    COLLATE utf8mb4_0900_bin NULL COMMENT '처음 클리어한 판 번호(UUID). 판 표에 외래 키 없음',
    created_at                DATETIME(3) NOT NULL,
    updated_at                DATETIME(3) NOT NULL,
    PRIMARY KEY (account_stage_progress_id),
    CONSTRAINT uk_account_stage_progress_account_stage UNIQUE (account_id, stage_id),
    CONSTRAINT fk_account_stage_progress_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT ck_account_stage_progress_stage_id CHECK (REGEXP_LIKE(stage_id, '^[a-z0-9.]{1,64}$', 'c')),
    CONSTRAINT ck_account_stage_progress_run_id CHECK (REGEXP_LIKE(first_clear_run_id,
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
