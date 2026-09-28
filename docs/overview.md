# 웹서버 구조

> 상태: **초안**. Spring Boot 프로젝트 생성 후 채웁니다.

## 역할

- DS 등록·상태 관리 → 루트 `docs/contracts/ds-registry-api.md`
- 게임 결과 저장 → 루트 `docs/contracts/result-api.md`
- Redis 사용 → 루트 `docs/contracts/redis-keys.md`

## 기술 스택

| 항목 | 값 |
|---|---|
| 프레임워크 | Spring Boot (버전 TBD) |
| 언어·Java 버전 | TBD |
| 빌드 도구 | TBD (Gradle / Maven) |
| DB | 로컬: MySQL 8.4 (`compose.yaml`), 운영: TBD |
| 캐시·상태 | Redis 7.4 |

## 패키지 구조

TBD

## 환경변수

| 이름 | 용도 | 예시 파일 |
|---|---|---|
| `MYSQL_*` | 로컬 DB 접속 | `.env.example` |
| `REDIS_PORT` | 로컬 Redis 포트 | `.env.example` |

새 환경변수를 추가하면 이 표와 `.env.example`에 함께 적습니다.
