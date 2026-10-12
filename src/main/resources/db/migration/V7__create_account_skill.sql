-- 번호는 PR 직전에 dev의 마지막 번호 + 1로 맞춘다(docs/migrations.md).
-- S4 스킬 해금: 계정마다 해금한 스킬 한 줄(지시서 1-1a, 계정 + 종류별 자식 표). 명세는 루트 docs/contracts/skill-api.md.
-- 기본 키(계정 + 스킬)가 "같은 스킬은 한 번만 해금"의 마지막 방어다. 동시에 같은 스킬을 해금하면 하나는 기본 키에 걸린다.
-- 락은 진행 행(account_progress.version)에만 둔다. 해금하면 포인트가 빠지므로 진행 행 version이 같이 오른다.
-- request_id는 처음 해금한 요청 번호다. 같은 요청이 다시 오면(기억 시간이 지난 뒤) ALREADY_SAVED로 알아본다.
-- skill_tag는 UE 게임플레이 태그 이름이고 대소문자를 가린다(utf8mb4_0900_bin).

CREATE TABLE account_skill
(
    account_id BIGINT       NOT NULL COMMENT 'account.account_id',
    skill_tag  VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'master/skills.json 스킬 태그 = UE 게임플레이 태그(예: Account.Skill.Combo4)',
    request_id CHAR(36)     COLLATE utf8mb4_0900_bin NOT NULL COMMENT '해금한 요청의 requestId(소문자 UUID)',
    created_at DATETIME(3)  NOT NULL COMMENT '해금 시각',
    updated_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (account_id, skill_tag),
    CONSTRAINT fk_account_skill_account FOREIGN KEY (account_id) REFERENCES account (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 누적 통계(account_stat_total)에 S4 해금 조건 재료 키 3개를 더한다. 표는 그대로, 키(줄)만 늘어난다.
--   stage_play.play_time_ms  : 플레이 시간 합계(밀리초)
--   stage_play.max_wave      : 가장 멀리 간 웨이브(최댓값)
--   stage_play.best_clear_ms : 가장 빠른 클리어 시간(최솟값, 클리어한 결과만. 클리어한 적이 없으면 줄이 없음)
-- 지금까지의 결과(stage_play_result)로 처음 값을 채운다. 앞으로는 결과 저장 때 AccountService가 더한다.
INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
SELECT account_id, 'stage_play.play_time_ms', SUM(play_time_ms), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM stage_play_result
GROUP BY account_id;

INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
SELECT account_id, 'stage_play.max_wave', MAX(reached_wave), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM stage_play_result
GROUP BY account_id;

INSERT INTO account_stat_total (account_id, stat_key, total, created_at, updated_at)
SELECT account_id, 'stage_play.best_clear_ms', MIN(play_time_ms), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM stage_play_result
WHERE cleared = TRUE
GROUP BY account_id;
