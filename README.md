# PW01WebServer

ProjectWarrior(UE 5.8.3 싱글 게임)의 Spring Boot 백엔드입니다. 게임이 보낸 계정·결과·성장 요청을 검증·판정하고 MySQL·Redis에 저장합니다.

- 작업 공간 루트: https://github.com/NBCGameServerCamp09Team01/PW01 (이 폴더는 루트 안의 `PW01\PW01WebServer\`)
- 게임과의 약속(API·Redis 키·오류 형식): 루트 `docs/contracts/` — **코드보다 명세를 먼저 고칩니다**
- 작업 규칙: [AGENTS.md](AGENTS.md), 루트 `docs/git-workflow.md`. 이 저장소의 기준 브랜치는 **`dev`** 입니다
- 결정 기록: [docs/decisions/0001-초기-설정.md](docs/decisions/0001-초기-설정.md)
- **세팅 명세**(파일·의존성·설정이 각각 무슨 역할인지): [docs/initial-setup.md](docs/initial-setup.md)

| 항목 | 값 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 (Gradle 9.5.1, Groovy DSL) |
| DB | MySQL 8.4 + Flyway |
| 캐시·상태 | Redis 7.4 |
| 테스트 | JUnit 6, Testcontainers(MySQL·Redis) |

## 처음 받기

### 1. 준비물

- **JDK 21**: IntelliJ의 File → Project Structure → SDKs → Download JDK에서 21을 받으면 됩니다(배포판은 상관없음). IntelliJ가 기본 위치(사용자 폴더의 `.jdks`)에 받은 JDK는 터미널의 `java`가 다른 버전이어도 Gradle이 찾아 씁니다. `.\gradlew -q javaToolchains`로 확인할 수 있고, 못 찾으면 환경 변수 `JAVA_HOME`을 JDK 21 폴더로 잡습니다.
- **Docker Desktop**: 실행 중이어야 compose와 테스트가 돕니다.
- IntelliJ는 이 폴더(`PW01WebServer`)를 엽니다. 루트(`PW01`)는 IDE로 열지 않습니다.

### 2. 받기

새로 받을 때는 루트에서 `tools\setup.bat web`을 실행하거나, `PW01\` 안에서:

```bat
git clone https://github.com/NBCGameServerCamp09Team01/PW01WebServer.git
```

이미 받아 둔 경우에는 이 폴더에서:

```bat
git fetch
git switch dev
git pull --ff-only
```

git 명령은 이 폴더(`PW01\PW01WebServer\`) 안에서 실행합니다.

### 3. 로컬 MySQL·Redis 띄우기

```bat
copy .env.example .env
```

`.env`를 열어 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD` 값을 채운 뒤(`.env`는 커밋되지 않습니다):

```bat
docker compose up -d
docker compose ps
```

- MySQL은 `127.0.0.1:3306`, Redis는 `127.0.0.1:6379`입니다.
- 3306을 이미 다른 MySQL(예: Windows의 MySQL80 서비스)이 쓰고 있으면 `.env`에 `MYSQL_PORT=3307`처럼 다른 포트를 적습니다([트러블슈팅](docs/troubleshooting/README.md)).
- 멈추기는 `docker compose down`, 데이터까지 지우기는 `docker compose down -v`입니다.

### 4. 앱 실행 (IntelliJ)

`Pw01WebserverApplication` 실행 구성(Run → Edit Configurations)의 Environment variables에 `.env`와 같은 이름·값을 넣습니다.

- `MYSQL_PASSWORD=…;REDIS_PASSWORD=…`
- `.env`에서 포트를 바꿨다면 `MYSQL_PORT=3307`도 넣습니다. 빠지면 3306으로 붙어, PC에 따로 설치된 MySQL에서 `Access denied for user 'pw01'@'localhost'`가 납니다([트러블슈팅](docs/troubleshooting/README.md)).
- 프로필은 정하지 않아도 `local`로 뜹니다(`spring.profiles.default=local`).

확인:

- http://localhost:8080/actuator/health → `{"status":"UP"}`
- Swagger UI(확인용): http://localhost:8080/swagger-ui/index.html

### 5. 빌드·테스트

```bat
.\gradlew build
```

cmd와 PowerShell(IntelliJ 터미널 기본) 모두 `.\gradlew`로 실행합니다. 테스트가 MySQL·Redis 컨테이너를 잠깐 띄우므로 Docker Desktop이 켜져 있어야 합니다. PR을 올리면 GitHub Actions(CI)가 같은 빌드를 돌립니다.

## 구조

```
src/main/java/com/pw01/webserver/
├─ Pw01WebserverApplication.java
├─ common/                ← 여러 기능이 같이 쓰는 것
│   ├─ entity/            ← BaseEntity(생성·수정 시각)
│   └─ error/             ← 공통 오류 응답, 상태별 예외, GlobalExceptionHandler
├─ config/                ← 설정(JpaAuditingConfig)
└─ example/               ← 학습용 예시 API(삭제 예정): controller · service · repository · entity · dto
src/main/resources/
├─ application.properties         ← 공통 설정
├─ application-local.properties   ← 각자 PC(compose)
├─ application-prod.properties    ← 배포
└─ db/migration/                  ← Flyway 마이그레이션
src/test/java/com/pw01/webserver/ ← @IntegrationTest(Testcontainers), @WebMvcTest
```

- 상태 저장소: MySQL(영구 데이터), Redis(빠르게 바뀌는 상태. 키 규칙은 루트 `docs/contracts/redis-keys.md`)
- 프로필·환경변수·요청 흐름: [docs/overview.md](docs/overview.md)

## API

- 명세 원본은 루트 `docs/contracts/`입니다. 공통 규칙(경로는 기능 이름부터·버전 없음, 시각 UTC, 오류 응답 `{code, message, path, errors}`)은 그 폴더의 README.md에 있습니다.
- Swagger UI는 실행 중인 서버를 확인하는 보기입니다. 명세와 다르면 명세가 기준입니다.
- 지금 있는 API는 학습용 예시 `/api/v1/examples`입니다(경로 규칙이 바뀌기 전에 만든 옛 경로, S1 때 지움). 따라 하기: [docs/guides/example-api.md](docs/guides/example-api.md)

## 제약·인덱스와 이유

| 테이블 | 제약·인덱스 | 이유 |
|---|---|---|
| `example` | `uk_example_name` (name 유일) | 같은 이름을 두 번 만들지 않는다. 동시 요청은 DB 제약이 마지막으로 막는다(학습용) |

테이블을 더하면 이 표에 함께 적습니다.

## 검증 숫자

| 항목 | 값 | 측정 |
|---|---|---|
| 테스트 | 17개 통과, 실패 0 (스모크 2, 연결 2, 공통 오류 틀 7, 예시 API 6) | `.\gradlew build`, 2026-10-06 |
| 예시 API 실행 | `POST /api/v1/examples` 201 + `Location`, `createdAt`은 밀리초까지 UTC | local 프로필 + compose, 2026-10-06 |

## 주요 쟁점

개발하면서 부딪힌 설계 쟁점과 고른 이유를 적습니다. (아직 없음)
