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
| V5 | `V5__create_stage_play_result.sql` | S2 스테이지 플레이·결과 요약·변경 내역·누적 통계 | Robbie·Sang-Hyun-Kim | #11 | 병합됨 |
| V6 | `V6__create_account_stat.sql` | S3 스탯 투자(계정별 스탯 포인트) | Robbie | #14 | 병합됨 |
| V7 | `V7__create_account_skill.sql` | S4 스킬 해금(계정별 해금 스킬), 누적 통계 키 3개 처음 값 | Robbie | #15 | 병합됨 |
| V8 | `V8__create_ranking_tables.sql` | 랭킹(M7) 표 5개: 클리어 기록·스테이지 최고·지역 점수·전체 총합·진행 | Sang-Hyun-Kim | (사후 리뷰) | 병합됨(V7에서 옮김) |

## 예외 기록

- **10/12 랭킹 V7 → V8.** 랭킹이 PR 없이 `V7__create_ranking_tables.sql`로 `dev`에 들어갔고(19:49), 이어 S4 #15가 `V7__create_account_skill.sql`로 병합되어(19:56) `dev`에 V7이 두 개가 됐습니다. Flyway가 기동에서 멈춰 서버·통합 시험이 모두 실패했습니다. 담당자 결정으로 랭킹을 V8로 옮겼습니다(병합된 파일은 고치지 않는다는 규칙의 예외).
  - 로컬 DB에 랭킹이 V7로 들어간 적이 있으면(그 사이 `dev`로 서버를 띄움) 기동 검사에서 멈춥니다. `docker compose down -v` 뒤 `docker compose up -d`로 다시 만듭니다.
  - S4 브랜치로만 띄운 DB(V7 = 스킬)는 그대로 두면 V8만 더해집니다.
