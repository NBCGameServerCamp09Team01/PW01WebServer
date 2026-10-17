# 0003. 길드 동시성

- 상태: 채택(#31 길드 Lv1, #33 접속 실시간. 10/17 `dev` 병합 #37)
- 날짜: 2026-10-16
- 결정: Sang-Hyun-Kim(길드 담당). 명세는 루트 `docs/contracts/guild-api.md` v1.1 "동시에 눌렀을 때"·"한 번만 반영", 표는 [erd/guild.md](../erd/guild.md)

## 배경

- 길드는 여러 사람이 같은 행을 동시에 고칩니다. 길드장이 두 기기에서 동시에 수락하거나, 두 길드가 한 사람을 동시에 수락하거나, 수락과 해체가 겹칩니다. 서버 여러 대가 같은 MySQL을 씁니다.
- InnoDB 기본 격리 수준(REPEATABLE READ)에서 일반 `SELECT`는 트랜잭션 첫 읽기 때의 스냅숏을 봅니다. "읽어 보니 29명이라 넣는다"는 판단은 그사이 다른 커밋을 못 봅니다(lost update). 쓰는 순간에 다시 확인하는 장치가 필요합니다.

지켜야 할 것:

| # | 불변 조건 | 지키는 장치 |
|---|---|---|
| I-1 | 길드원 수 ≤ 정원 | `guild.version` + CHECK `ck_guild_member_count` |
| I-2 | 한 사람 한 길드 | `account_guild` PK = 계정 + `account_guild.version` |
| I-3 | `guild.member_count` = 실제 길드원 수 | 같은 트랜잭션에서 둘 다 고침, `guild.version` |
| I-4 | 이름 유일(대소문자 가림) | `uk_guild_name`(bin 정렬 규칙) |
| I-5 | 서버 길드 수 ≤ 상한 | `guild_counter.version` |
| I-6 | 한 사람 대기 중 신청 ≤ 3 | 신청 때 내 `account_guild` 행을 `PESSIMISTIC_FORCE_INCREMENT`로 먼저 잠금 |
| I-7 | 같은 길드에 대기 신청 하나 | `uk_guild_join_request_account_guild` |
| I-8 | 해체된 길드에 길드원·신청이 생기지 않음 | 해체는 `guild` 행 `FOR UPDATE` 먼저, 신청은 외래 키 |
| I-9 | 길드장 없는 길드 없음 | 길드원이 있으면 길드장 탈퇴 금지(409), 혼자면 해체 |
| I-10 | 같은 요청은 한 번만 반영 | 본문 `requestId` + `IdempotencyService` |

## 결정

| 번호 | 항목 | 결정 |
|---|---|---|
| D01 | 주력 방법 | **낙관적 잠금**: `guild`·`account_guild`·`guild_counter`에 `@Version`. 부딪히면(`UPDATE … WHERE version = v`가 0행) 기존 `ProgressWriter`가 **새 트랜잭션**으로 처음부터 다시(기본 3번, `pw01.progress.save-attempts`), 넘으면 409 `VERSION_CONFLICT`(`retryable: true`). 수락·거절·해체는 길드장 한 명만 하고 계정당 연결이 하나라 경합이 드물고, 계정 진행(`account_progress.version`)과 같은 틀이다 |
| D02 | 해체 | **비관적 잠금**: `guild` 행을 `SELECT … FOR UPDATE`로 맨 먼저 잡고, 길드원 소속 비우기(벌크 UPDATE, version +1) → 신청 지우기(벌크 DELETE) → `guild` DELETE → `guild_counter` −1. 여러 행을 지우기 전에 새 길드원이 끼지 못하게 붙잡아야 해서 낙관적 잠금(마지막 DELETE에서야 확인)으로는 순서가 맞지 않음 |
| D03 | 가입 신청 | 내 `account_guild` 행을 `PESSIMISTIC_FORCE_INCREMENT`(`FOR UPDATE` + 곧바로 version UPDATE)로 잡은 뒤 판정. 같은 사람의 동시 신청이 차례로 서서 "대기 중 3건" 개수가 정확함 |
| D04 | 잠금 순서 | 모든 쓰기가 **`guild_counter` → `guild` → `account_guild` → `guild_join_request`** 순서로 잡는다(아래 표). 해체가 `guild_counter`를 맨 뒤에 잡는 것은 예외지만, 만들기가 잡는 `guild` 행은 새로 만드는 행이라 같은 행을 두고 엇갈리지 않음 |
| D05 | 서버 길드 수 | `COUNT(*)`가 아니라 행 하나짜리 `guild_counter`의 version으로 동시 만들기를 하나씩 통과시킴 |
| D06 | 최종 방어 | PK·UNIQUE·FK·CHECK. 서비스 판정이 뚫려도 잘못된 데이터는 커밋되지 않음. 유일·외래 키 위반은 다시 해도 같으므로 `ProgressWriter`의 `retryOn = false`로 다시 하지 않고 제약 이름을 보고 409·404로 바꿈(`GuildCommandService.translate`) |
| D07 | 교착 | MySQL 교착(1213)·잠금 대기 초과(`PessimisticLockingFailureException`)도 `ProgressWriter`가 다시 함 |
| D08 | 알림 | 받는 사람·data는 트랜잭션 안에서 계산해 `GuildRealtimeNotice`·생애 이벤트로 내고, 받는 쪽은 `@TransactionalEventListener(AFTER_COMMIT)`. 다시 하는 동안 롤백된 시도의 알림은 사라져 두 번 나가지 않음 |
| D09 | 트랜잭션 경계 | `GuildCommandService`의 쓰기 메서드에는 `@Transactional`을 붙이지 않는다. 붙이면 `ProgressWriter`의 새 트랜잭션이 바깥에 묶여 같은 스냅숏으로 다시 해 계속 실패 |
| D10 | 접속 표시 | 원본은 Redis 접속 상태 키(서버 번호, TTL 150초). 바뀜 판정은 `SET … GET`(전 값 없음 = 접속)과 `presence_release.lua`(지웠음 = 끊김)로 한 명령씩. DB 잠금과 섞지 않음 |

| 동작 | 순서대로 잡는 행 | 방식 |
|---|---|---|
| 만들기 | `guild_counter`(version +1, flush) → 새 `guild` INSERT → 내 `account_guild`(LEADER) → 내 신청 삭제 | 낙관 |
| 신청 | 내 `account_guild`(`PESSIMISTIC_FORCE_INCREMENT`) → `guild_join_request` INSERT(외래 키 검사로 `guild`에 공유 잠금) | 비관 먼저 |
| 수락 | `guild`(인원 +1, **version UPDATE 먼저 flush**) → 신청 확인 → 신청자 `account_guild`(MEMBER) → 신청자의 신청 모두 삭제 | 낙관 |
| 탈퇴 | `guild`(인원 −1, 먼저 flush) → 내 `account_guild`(무소속) | 낙관 |
| 해체 | `guild`(`FOR UPDATE`) → 길드원 `account_guild`(벌크) → `guild_join_request`(벌크) → `guild` DELETE → `guild_counter` −1 | 비관 먼저 |

## 구현하며 피한 함정

| # | 함정 | 대비 |
|---|---|---|
| P-1 | 수락이 `account_guild.guild_id`를 바꾸면 InnoDB가 외래 키 검사로 `guild` 행에 공유 잠금을 건다. 두 수락이 공유 잠금을 쥔 채 `guild` version을 올리려(배타 잠금) 하면 교착 | `guild` 행 version UPDATE를 먼저 `saveAndFlush`해 배타 잠금부터 잡음(`GuildMembershipTx.approve`) |
| P-2 | Hibernate는 flush 때 INSERT → UPDATE → DELETE 순으로 보냄(코드 순서와 다름) | 순서가 중요한 곳은 `saveAndFlush`·`flush()`로 끊음 |
| P-3 | 벌크 JPQL은 영속성 컨텍스트를 건너뜀 | 해체 벌크는 flush·clear, 벌크 뒤 엔티티를 다시 쓰지 않음. 수락·만들기의 신청 삭제는 clear하지 않음(같은 트랜잭션의 엔티티 저장이 빠지지 않게) |
| P-4 | REPEATABLE READ에서 `insertIfAbsent` 직후 일반 읽기로는 다른 트랜잭션이 넣은 행이 안 보임 | 잠금 읽기(`findLockedById`, 최신 커밋 값)로 다시 읽음 |
| P-5 | 가입 이벤트를 `try` 안에서 내면 받는 쪽의 제약 위반이 "아이디 중복" 409로 바뀜 | `AccountService.register`가 저장 성공 뒤 `try` 밖에서 냄 |
| P-6 | 커밋된 마이그레이션은 주석만 고쳐도 Flyway 체크섬이 바뀜 | 실행된 적 있는 V10은 고치지 않음(주석의 "줄" 표현도 그대로) |

## `guild_counter` 몰림(10/16, 시험 C-5 뒤)

- 상한 5에서 열 명이 동시에 만들자 `guild_counter` 충돌 재시도가 21번, 그중 7건이 재시도 3번을 다 쓰고 409 `VERSION_CONFLICT`로 끝났다. 상한과 `guild_count` = 실제 행 수는 지켜졌다(틀린 결과 없음).
- 다른 안: ② `FOR UPDATE`(기다림), ③ 조건부 UPDATE(`SET guild_count = guild_count + 1 WHERE counter_id = 1 AND guild_count < :max`, 한 문장, 몰릴 때 처리량 가장 높음).
- **결정: ① 낙관적 잠금 유지.** 상한이 5이고 만들기는 드물다. 409는 `retryable: true`라 게임이 같은 본문으로 다시 보내면 되고, 다른 쓰기와 같은 틀이다.
- **바꿀 신호**: 상한을 크게 올리거나 이벤트로 만들기가 몰릴 때. 그때 `guild_counter`만 ③으로(`GuildMembershipTx.create`의 세 줄 + 리포지토리 메서드 하나). 정원(`guild.member_count`)도 같은 후보.

## 시험

`src/test/java/com/pw01/webserver/guild/GuildConcurrencyTest`(여러 스레드를 `CountDownLatch`로 같이 출발):

| # | 장면 | 확인 |
|---|---|---|
| C-1 | 정원 5 길드에 신청 8건 동시 수락 | 성공 ≤ 4, `member_count` = 실제 수 |
| C-2 | 한 사람을 두 길드가 동시 수락 | 한쪽만 성공 |
| C-3 | 수락과 해체 동시 | 해체 성공, 남는 행 없음, 길드 수 −1 |
| C-4 | 같은 이름 동시 만들기 5 | 하나만 성공 |
| C-5 | 상한 5에서 다른 이름 동시 만들기 10 | 성공 ≤ 5, `guild_count` = 성공 수 |
| C-6 | 한 사람이 네 길드에 동시 신청 | 정확히 3건 |
| C-7 | 같은 `requestId` 동시 두 번 | 길드 하나 |
| C-8 | 탈퇴 3과 수락 3 동시 | `member_count` = 실제 수 |

교착 재시도는 `account/service/ProgressWriterTest`, 접속 바뀜 판정은 `presence/PresenceTest`·`guild/service/GuildPresenceNotifierTest`.

## 다시 볼 때

- 서버 길드 상한·정원을 크게 올릴 때(위 "몰림").
- 길드장 넘기기·초대·내보내기(G10)를 넣을 때: 길드원을 넣는 길은 `GuildMembershipTx.approve` 하나로 두고(초대 수락도 여기로), 잠금 순서 표에 한 줄을 더한다.
- 길드원 목록 캐시를 넣을 때: 지금은 캐시가 없어 내보낸 사람이 길드 채팅을 계속 받는 일이 없다. 넣는다면 "읽을 때 채우고, 바뀌면 커밋 뒤 지우기만"(루트 `redis-keys.md` "길드").
