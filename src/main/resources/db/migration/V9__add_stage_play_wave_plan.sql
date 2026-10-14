-- M24: 스테이지 플레이에 시작 때의 웨이브 구성과 그 버전을 저장한다(루트 docs/contracts/stage-play-api.md "웨이브 구성").
-- 구성의 원본은 master/stage-waves.json·monsters.json이고, 플레이는 시작 순간의 값을 고정해 끝까지 쓴다
-- (재전송·이어하기·체크포인트에서 이어하기가 모두 같은 구성. 그 사이 서버 정의가 바뀌어도 그대로).
-- wave_plan은 몬스터 정의 값을 풀어 둔 웨이브 목록(JSON 배열, 응답 waves와 같은 칸). wave_count와 같은 정의에서 같이 쓴다.
-- 이 마이그레이션 전에 시작한 플레이는 둘 다 NULL이고, 응답할 때 지금 정의로 채운다(StagePlayService).
ALTER TABLE stage_play
    ADD COLUMN wave_plan    JSON        NULL COMMENT '시작 때의 웨이브 구성(stage-play-api.md waves와 같은 모양). V9 전 플레이는 NULL' AFTER wave_count,
    ADD COLUMN wave_version VARCHAR(32) NULL COMMENT '시작 때의 웨이브 정보 버전(w{stage-waves}-m{monsters}). V9 전 플레이는 NULL' AFTER wave_plan,
    ADD CONSTRAINT ck_stage_play_wave_plan_pair CHECK ((wave_plan IS NULL) = (wave_version IS NULL)),
    ADD CONSTRAINT ck_stage_play_wave_plan_array CHECK (wave_plan IS NULL OR JSON_TYPE(wave_plan) = 'ARRAY');
