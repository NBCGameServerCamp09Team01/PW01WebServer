# 웹서버 구조

> 상태: **초안**(2026-10-06). 기능이 늘면 패키지·환경변수 표를 함께 고칩니다.

## 역할

- 게임 클라이언트가 호출하는 API → 명세는 루트 `docs/contracts/`
- 게임 결과 저장 → 루트 `docs/contracts/result-api.md`
- Redis 사용 → 루트 `docs/contracts/redis-keys.md`

## 기술 스택

| 항목 | 값 |
|---|---|
| 프레임워크 | Spring Boot 4.1.1 |
| 언어·Java 버전 | Java 21 (Gradle 툴체인) |
| 빌드 도구 | Gradle 9.5.1 (Groovy DSL, Wrapper) |
| DB | 로컬: MySQL 8.4(`compose.yaml`), 테스트: Testcontainers MySQL 8.4, 운영: TBD |
| 스키마 | Flyway(`src/main/resources/db/migration`). Hibernate는 검사만(`ddl-auto=validate`) |
| 캐시·상태 | Redis 7.4 |
| API 확인 | springdoc Swagger UI(확인용 보기. 원본은 루트 `docs/contracts/`) |

## 패키지 구조

```
com.pw01.webserver
├─ common/entity   BaseEntity(생성·수정 시각, UTC)
├─ common/error    ErrorResponse, CommonErrorCode, 상태별 예외, GlobalExceptionHandler
├─ common/master   MasterDataLoader(resources/master/*.json을 기동 때 읽음, 틀리면 기동 실패. SF-7)
├─ common/chat     M23 채팅·길드 두 모듈 사이 약속: ChatMessage·ChatScope·ChatHistoryPage, GuildDirectory(소셜이 구현)·GuildChatHistory(채팅이 구현),
│                  GlobalChatHistory(지금은 NoStoreGlobalChatHistory — 전체 채팅 저장 안 함)
├─ config          JpaAuditingConfig, RealtimeConfig(/realtime), RealtimeRelayConfig(서버 사이 전달 구독)
├─ realtime/       S7 실시간 연결. 보내기 입구 RealtimeNotifier(send·sendToAccounts·sendToGuild·sendToAll) → 이 서버 연결(RealtimeLocalDelivery)
│                  + Redis Pub/Sub(RealtimeRelayPublisher → 모든 서버의 RealtimeRelayListener). 연결 이벤트 realtime/event. SF-11
├─ stage/          지역·스테이지 정보(master/stages.json), 웨이브 구성·몬스터 정의(stage-waves.json·monsters.json, M24), 난이도(difficulties.json, M9), ST1 GET /stages와 계정별 클리어 진행(ST2 GET /accounts/me/stages)
├─ ranking/        랭킹(스테이지·지역·전체 총합·진행, 전체·주간). 원천 MySQL 표 5개(V8) + Redis 보드 사본(pw01:rank:*, rank_*.lua).
│                  JPA 엔티티 없이 JdbcClient. 결과 저장(StageResultService)이 같은 트랜잭션에서 RankingRecordService.recordClear를 부른다. 결정 기록 decisions/0002
├─ chat/           M23 채팅(루트 chat-api.md CH1·CH2·CH3). ChatService: 글 규칙(ChatTextPolicy) → 한 번만 반영 → 횟수 제한·도배 쉬기(RedisChatRateLimiter, chat_rate.lua)
│                  → 길드 채팅은 guild_chat_message(V11, GuildChatHistoryImpl)에 저장하고 커밋 뒤 sendToGuild, 전체는 저장 없이 sendToAll
│                  지우기: 해체 커밋 뒤 ChatDisbandListener, 하루 한 번 3일 지난 줄 GuildChatCleanupJob(전용 스케줄러, 1000줄씩 새 트랜잭션)
├─ guild/          M23 길드 G1~G12(G10 보류). 원본 MySQL 표 넷(V10, erd/guild.md). 쓰기는 GuildCommandService(중복 방지 → ProgressWriter)
│                  → GuildMembershipTx(트랜잭션 몸통, 잠금 순서). GuildDirectory 구현·생애 이벤트(common/chat)·GUILD_* 알림(커밋 뒤). 결정 기록 decisions/0003
├─ presence/       누가 실시간 연결을 갖고 있나(Redis pw01:presence:<계정>). 연결 이벤트(realtime/event)를 받아 씀, 바뀌면 PresenceChangedEvent
│                  → guild의 GuildPresenceNotifier가 PRESENCE_CHANGED로 길드원에게
└─ <기능>/          controller · service · repository · entity · dto  (account·auth·stage·stageplay·guild·chat 등)
```

## 요청 흐름

Controller(요청 DTO 검증) → Service(판정·트랜잭션) → Repository → DB 순서로 흐릅니다. 거절은 상태별 예외로 던지고, `GlobalExceptionHandler`가 공통 오류 응답 `{code, message, path, errors}`으로 바꿉니다. 파일 순서대로 따라가려면 `auth/`(가입·로그인)·`account/`(메인화면 값) 패키지를 보세요.

## 프로필

| 프로필 | 언제 | 접속 대상 |
|---|---|---|
| `local` | 각자 PC. 프로필을 정하지 않으면 이 프로필(`spring.profiles.default`) | `compose.yaml`의 MySQL·Redis |
| `test` | 테스트(`@IntegrationTest`) | Testcontainers가 띄운 MySQL·Redis |
| `prod` | 배포(`SPRING_PROFILES_ACTIVE=prod`) | 배포 환경 변수 |

## 환경변수

| 이름 | 쓰는 곳 | 비고 |
|---|---|---|
| `MYSQL_DATABASE`, `MYSQL_USER` | compose, local | 기본값 `pw01` |
| `MYSQL_PASSWORD` | compose, local | 기본값 없음 |
| `MYSQL_ROOT_PASSWORD` | compose | 기본값 없음 |
| `MYSQL_PORT` | compose, local | 기본 3306. 다른 MySQL이 3306을 쓰면 바꾼다(예: 3307) |
| `REDIS_PORT` | compose, local, prod | 기본 6379(compose·local) |
| `REDIS_PASSWORD` | compose, local, prod | 기본값 없음 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST` | prod | 배포 환경에서 넣는다(배포 계획 D-4) |
| `SPRING_PROFILES_ACTIVE` | prod | 배포에서 `prod`. 정하지 않으면 `local` |
| `MANAGEMENT_PORT` | prod | Actuator 관리 포트. 기본 8081. 밖에 열지 않는다 |
| `APP_COMMIT` | 이미지 | 이미지를 만든 커밋(`/actuator/info`의 `app.commit`). 이미지 빌드 때 넣고, 없으면 `local` |

새 환경변수를 추가하면 이 표와 `.env.example`(로컬에서 쓰는 것이면)에 함께 적습니다.
