# 마이그레이션 번호 표

`src/main/resources/db/migration`의 Flyway 파일 목록입니다. 기준은 `dev`의 폴더(마지막 번호)입니다.

## 규칙

- 이어지는 번호를 씁니다. 작업 중에는 아무 번호나 쓰고, **PR 직전에 `dev`의 마지막 번호 + 1로 맞춥니다**.
- `dev`에 병합된 파일은 고치지 않고 새 파일을 더합니다.
- 마이그레이션이 든 PR을 올리거나 병합할 때 디스코드에 "V○ 씀"을 알리고, 이 표에 한 줄 더합니다.
- 두 PR이 같은 번호면 늦게 병합하는 쪽이 `dev`를 받아 번호를 올립니다(같은 번호는 CI 시험이 실패해서 알려 줍니다).
- 순서가 꼬인 로컬 DB는 `docker compose down -v` 뒤 `docker compose up -d`로 다시 만듭니다(로컬 데이터만 사라짐).

## 번호

| 번호 | 파일 | 기능 | 담당 | PR | 상태 |
|---|---|---|---|---|---|
| V1 | `V1__example.sql` | 학습용 예시 표 | Sang-Hyun-Kim | #1 | 병합됨 |
| V2 | `V2__create_account.sql` | S1 계정·계정 진행 | Sang-Hyun-Kim | #4 | 병합됨 |
| V3 | `V3__drop_example.sql` | S1 정리: 학습용 예시 표 삭제 | Robbie | #8 | 병합됨 |
| V4 | `V4__create_account_stage_progress.sql` | 스테이지 진행(클리어) | Sang-Hyun-Kim | #9 | 병합됨 |
