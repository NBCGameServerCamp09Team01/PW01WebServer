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

- http://localhost:8080/actuator/health → `"status":"UP"`, `components`에 `db`·`redis`도 각각 `UP`
- Swagger UI(확인용): http://localhost:8080/swagger-ui/index.html

운영 확인(local은 8080, `prod`는 관리 포트 8081에서만):

- 상태·버전: `/actuator/health`(DB·Redis 하위 상태), `/actuator/info`(버전·커밋), `/actuator/metrics`, `/actuator/loggers`
- 저장소가 막혔을 때: `docker compose stop redis` → health `DOWN` → API가 503. 응답 헤더 `X-Request-Id` 값으로 콘솔 로그를 찾으면 `저장소에 닿지 못함` 줄이 나온다. 끝나면 `docker compose start redis`
- 로그 수준 바꾸기: `POST /actuator/loggers/com.pw01.webserver`에 `{"configuredLevel":"DEBUG"}`, 되돌릴 때 `{"configuredLevel":null}`

### 5. 빌드·테스트

```bat
.\gradlew build
```

cmd와 PowerShell(IntelliJ 터미널 기본) 모두 `.\gradlew`로 실행합니다. 테스트가 MySQL·Redis 컨테이너를 잠깐 띄우므로 Docker Desktop이 켜져 있어야 합니다. PR을 올리면 GitHub Actions(CI)가 같은 빌드를 돌립니다.

### 6. 이미지 (선택 — 배포용 컨테이너를 로컬에서 볼 때만)

평소 개발에는 필요 없습니다. `dev`에 병합되면 GitHub Actions(`build-push-ecr.yml`)가 이미지를 만들어 AWS ECR `pw01/webserver`에 올립니다(태그 `dev-<커밋 해시>`, 배포는 하지 않음).

```bat
docker build -t pw01/webserver:local --build-arg APP_COMMIT=<커밋 해시> .
docker run --rm --network pw01-local_default --env-file .env.docker -p 8080:8080 -p 8081:8081 pw01/webserver:local
```

- 3번의 compose(MySQL·Redis)가 떠 있어야 합니다. `--network`는 compose 네트워크 이름입니다(`docker network ls`).
- `.env.docker`는 견본에서 만듭니다: `copy .env.docker.example .env.docker` → `DB_PASSWORD`·`REDIS_PASSWORD`에 `.env`와 같은 값(`MYSQL_PASSWORD`·`REDIS_PASSWORD`)을 넣습니다. `.env.docker`는 커밋되지 않습니다.
- 실행 중인 IntelliJ 앱과 8080이 겹치면 먼저 멈춥니다.

### 7. 로컬 로드 밸런서 환경 (선택 — 서버 두 대 동작을 볼 때)

배포는 로드 밸런서 뒤에 서버가 여러 대입니다. 실시간 메시지가 다른 서버에 붙은 사람에게도 가는지(서버 사이 전달, 루트 `docs/flows/realtime-relay.md`) 보려면 PC에서 같은 모양을 띄웁니다. `compose.yaml`은 그대로 두고 `compose.apps.yaml`을 겹쳐 씁니다.

```bat
docker compose -f compose.yaml -f compose.apps.yaml up -d --build
docker compose -f compose.yaml -f compose.apps.yaml ps
curl.exe -si http://localhost:8080/health
```

| 주소 | 무엇 |
|---|---|
| `http://localhost:8080` | 로드 밸런서(nginx, 라운드 로빈·WebSocket·유휴 120초). 게임 PIE 기본 주소 그대로 |
| `http://localhost:8081` | app-a 바로(서버를 고를 때) |
| `http://localhost:8082` | app-b 바로 |

- 응답 헤더 `X-Upstream`이 두 앱을 번갈아 가리키면 정상입니다(로컬 nginx만 붙이는 헤더).
- 앱은 `local` 프로필로 뜨고 `.env`의 `MYSQL_PASSWORD`·`REDIS_PASSWORD`를 씁니다(따로 만들 파일 없음). 상태 확인은 `/health`, `/actuator/health` 모두 앱 포트입니다.
- 로그: `docker compose -f compose.yaml -f compose.apps.yaml logs -f app-a app-b`. 요청 하나는 `… logs app-a app-b | findstr <requestId>`. 뜰 때 찍히는 `[Relay] serverId=…`로 어느 앱인지 압니다.
- 서버를 정해 붙기: 게임 Standalone 실행 인자 `-ServerUrl=http://localhost:8082`, Postman WebSocket `ws://localhost:8082/realtime`(헤더 `Authorization: Bearer <토큰>`).
- 앱 하나 멈춤 시험: `… stop app-b` → 요청이 모두 app-a로 → `… start app-b` → 뜬 뒤 10초쯤 지나면 다시 번갈아 갑니다(nginx가 실패한 앱을 잠깐 빼 두었다가 다시 시도). nginx를 다시 띄울 때는 두 앱이 떠 있어야 합니다.
- 코드를 고쳤으면 첫 줄을 다시(`--build`). 끝낼 때는 `… stop`(`down -v`는 데이터를 지웁니다).
- 4번의 IntelliJ 앱과 8080이 겹치므로 먼저 멈춥니다. PC 메모리 약 2.5~3GB를 씁니다.

**웨이브 정의를 재시작 없이 바꿔 보기(파일 출처, 선택)** — 배포 서버는 웨이브 구성(`stage-waves.json`)을 S3에서 읽고 재시작 없이 바꿉니다(루트 `docs/contracts/stage-api.md` "웨이브 구성 바꾸기"). 로컬에서는 S3 대신 PC의 파일 하나를 두 앱이 함께 보게 해서 같은 흐름(다시 읽기 신호 → 다른 앱에 알림 → 교체, 검사 실패면 유지, 5분 안전망)을 AWS 없이 해 봅니다. 기본은 지금처럼 이미지 안 리소스(classpath)라 켜지 않으면 아무것도 달라지지 않습니다.

```bat
:: 처음 한 번: 리소스 파일을 복사(local/content 는 git이 무시)
mkdir local\content
copy src\main\resources\master\stage-waves.json local\content\
:: 파일 출처로 띄우기(PowerShell은 $env:STAGE_CONTENT_SOURCE="file" 다음 줄에 compose 명령). 안전망을 짧게 보려면 STAGE_CONTENT_SAFETY_POLL=20s
set STAGE_CONTENT_SOURCE=file
docker compose -f compose.yaml -f compose.apps.yaml up -d --build
curl.exe -s http://localhost:8081/actuator/stagecontent
```

- 처음 띄울 때 Docker Desktop이 `local\content` 폴더 공유를 물으면 허용합니다(거절하면 `user declined directory sharing`으로 앱이 만들어지지 않음).
- 바꾸기: `local\content\stage-waves.json`을 고치고 `"version"`을 쓴 적 없는 숫자로 올려 저장 → `curl.exe -s -X POST http://localhost:8081/actuator/stagecontent` → `"result":"CHANGED"`. 몇 초 안에 `curl.exe -s http://localhost:8082/actuator/stagecontent`의 `waveVersion`도 같아집니다(app-b 로그 `[Relay] content changed`, `[Content] 정의 교체 … reason=relay`).
- 거절: version을 그대로 두고 내용만 바꾸면 `"result":"REJECTED"`, 두 앱 모두 이전 정의를 씁니다. 되돌리기는 파일을 원래대로 저장하고 다시 POST.
- 안전망: POST 없이 저장만 해 두면 안전망 주기(기본 5분) 안에 두 앱이 따라옵니다(`reason=poll`).
- 원래대로: `set STAGE_CONTENT_SOURCE=` 로 지우고 같은 `up -d` 명령을 다시 하면 classpath로 돌아갑니다.
- IntelliJ 앱 하나로 해 보려면 실행 구성 환경 변수에 `PW01_STAGECONTENT_SOURCE=file`, `PW01_STAGECONTENT_FILE_PATH=local/content/stage-waves.json`(Spring 환경 변수 이름은 대시가 빠짐).

## 구조

```
src/main/java/com/pw01/webserver/
├─ Pw01WebserverApplication.java
├─ common/                ← 여러 기능이 같이 쓰는 것
│   ├─ entity/            ← BaseEntity(생성·수정 시각)
│   └─ error/             ← 공통 오류 응답, 상태별 예외, GlobalExceptionHandler
├─ config/                ← 설정(JpaAuditingConfig)
├─ account/               ← 계정(메인화면 값): controller · service · repository · entity · dto
└─ auth/                  ← 인증(가입·로그인·세션·인터셉터): controller · service · repository · dto · interceptor
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
- 지금 있는 API는 S1 인증·계정입니다(`/auth/*`, `/accounts/me`). 명세: 루트 `docs/contracts/auth-api.md`·`account-api.md`

## 제약·인덱스와 이유

| 테이블 | 제약·인덱스 | 이유 |
|---|---|---|
| `account` | `uk_account_login_id` (login_id 유일, 대소문자 구분) · `uk_account_nickname` (nickname 유일) | 같은 아이디·닉네임을 두 번 만들지 않는다. 동시 가입은 DB 제약이 마지막으로 막는다(409로 바꿈) |

테이블을 더하면 이 표에 함께 적습니다.

## 검증 숫자

| 항목 | 값 | 측정 |
|---|---|---|
| 테스트 | 17개 통과, 실패 0 (스모크 2, 연결 2, 공통 오류 틀 7, 예시 API 6) | `.\gradlew build`, 2026-10-06 |
| 예시 API 실행 | `POST /api/v1/examples` 201 + `Location`, `createdAt`은 밀리초까지 UTC | local 프로필 + compose, 2026-10-06 |

## 주요 쟁점

개발하면서 부딪힌 설계 쟁점과 고른 이유를 적습니다. (아직 없음)
