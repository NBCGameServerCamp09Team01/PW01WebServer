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
├─ config          JpaAuditingConfig
└─ <기능>/          controller · service · repository · entity · dto  (지금은 학습용 example)
```

## 요청 흐름

Controller(요청 DTO 검증) → Service(판정·트랜잭션) → Repository → DB 순서로 흐릅니다. 거절은 상태별 예외로 던지고, `GlobalExceptionHandler`가 공통 오류 응답 `{code, message, path, errors}`으로 바꿉니다. 파일 순서대로 따라가는 예시는 [guides/example-api.md](guides/example-api.md)에 있습니다.

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

새 환경변수를 추가하면 이 표와 `.env.example`(로컬에서 쓰는 것이면)에 함께 적습니다.
