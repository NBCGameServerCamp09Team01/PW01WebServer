# AGENTS.md — PW01WebServer 작업 규칙

이 폴더 작업에서는 이 파일이 루트 `AGENTS.md`보다 우선합니다. 여기서 다루지 않는 부분은 루트 `AGENTS.md`를 따릅니다.

## 범위

- Spring Boot 백엔드. 게임 클라이언트(ProjectWarrior)가 호출하는 API와 MySQL·Redis 저장을 맡습니다.
- IDE(IntelliJ)는 이 폴더를 엽니다. 루트(`PW01`)는 IDE로 열지 않습니다.
- AI 에이전트는 게임 코드와 함께 읽으려고 루트에서 실행할 수 있습니다. 그때도 git 명령은 이 폴더 안에서 실행하고(`git rev-parse --show-toplevel`로 확인), 루트 저장소에 이 폴더 파일을 커밋하지 않습니다.

## 세션 시작 절차

1. 인수인계 문서가 있으면 먼저 읽고, 이 파일을 읽습니다.
2. `git status`와 `git log --oneline -5`로 실제 상태를 확인하고 문서와 대조합니다. 다르면 코드가 기준입니다.
3. 할 일과 바꿀 파일을 설명하고 사용자 확인을 받은 뒤 고칩니다.

## Git (AI 에이전트 규칙)

- 기준 브랜치는 `dev`입니다. 작업 브랜치 → Pull Request → `dev`. `main`은 릴리스용입니다(루트 `docs/git-workflow.md`).
- AI가 만든 변경은 PR로만 병합을 요청합니다. `dev`·`main`에 직접 커밋·push하지 않습니다.
- 병합 전에 리뷰 1명을 받습니다.
- CI 대상인 PR은 CI가 통과한 뒤에만 병합을 요청합니다. 문서만 바뀐 PR은 CI가 돌지 않습니다.
- force push와 브랜치 삭제를 하지 않습니다.
- 커밋·push는 사용자가 요청할 때만 합니다.
- 사람에게 적용할 GitHub 보호 설정은 팀 공유 뒤 정합니다.

## 비밀값

- DB 비밀번호, Redis 비밀번호, API 키, 토큰은 **환경변수**로만 받습니다. `application*.properties`에는 `${MYSQL_PASSWORD}` 같은 참조만 적고 기본값을 두지 않습니다.
- 로컬 값은 `.env`에 둡니다. `.env`는 커밋하지 않으며, 새 변수를 쓰면 `.env.example`에 **이름만** 추가합니다.
- 비밀값을 로그로 출력하지 않습니다. 요청·응답 로그에서 인증 헤더와 토큰은 가립니다.

## API 변경

- API나 Redis 키를 추가·변경할 때는 **루트 `docs/contracts/`의 명세를 먼저 고치고** PR 리뷰를 받은 뒤 코드를 바꿉니다.
- 명세와 코드가 다르면 명세가 기준입니다. 코드가 명세와 다르게 동작해야 한다면 명세를 고치는 PR부터 올립니다.
- 호환되지 않는 변경(필드 삭제·이름 변경)은 게임 담당자와 합의 없이 하지 않습니다.

## 코드

- Java 21(Gradle 툴체인), Spring Boot 4.1, Gradle 9.5.1(Groovy DSL). 빌드는 `gradlew build`.
- Boot 4에서 바뀐 것:
  - Jackson 3을 씁니다. 패키지는 `tools.jackson.*`이고, 애너테이션은 그대로 `com.fasterxml.jackson.annotation`입니다.
  - 웹 스타터 이름은 `spring-boot-starter-webmvc`입니다.
  - Hibernate 7, JUnit 6, Testcontainers 2를 씁니다.
  - MockMvc·`@WebMvcTest` 패키지는 `org.springframework.boot.webmvc.test.autoconfigure`입니다.
- 패키지: 기능 → 계층. `com.pw01.webserver.<기능>.{controller, service, repository, entity, dto}` + `common`(여러 기능이 같이 씀) + `config`.
- 3계층: Controller → Service → Repository 한 방향. Controller가 Repository를 부르지 않습니다. 다른 기능의 데이터는 그 기능의 Service로만 가져옵니다.
- DTO: 요청·응답 모두 record. 엔티티를 API 밖으로 내보내지 않습니다. 엔티티 → 응답 DTO 변환은 응답 DTO의 정적 메서드 `from(entity)`에 둡니다.
- 검증: 요청 DTO에 Bean Validation을 붙입니다. 게임 규칙 검증은 Service에서 합니다.
- 오류: 상태별 예외(`NotFoundException`·`ConflictException`·`ForbiddenException`·`InvalidRequestException`·`ServiceUnavailableException`)에 오류 코드(대문자와 `_`)를 담아 던집니다. 응답 본문 `{code, message, path, errors}`는 `GlobalExceptionHandler`가 만듭니다. 기능별 코드는 명세의 "오류" 표에 먼저 적습니다.
- 경로는 기능 이름부터 씁니다(예: `/auth/login`, `/accounts/me`). `/api`·버전(`/v1`)을 넣지 않습니다(루트 `docs/contracts/README.md`). 요청에 모르는 JSON 필드가 있으면 400입니다.
- 엔티티는 `BaseEntity`를 상속해 생성·수정 시각을 받습니다. enum은 문자열 칼럼(`@Enumerated(EnumType.STRING)`)으로 저장합니다.
- 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration/V{번호}__{설명}.sql`)으로만 바꿉니다(`ddl-auto=validate`). `dev`에 병합된 마이그레이션 파일은 고치지 않고 새 파일을 더합니다.
- 시간: JVM·DB·JDBC 모두 UTC입니다. 엔티티·DTO의 시각은 `Instant`, JSON은 `Z`가 붙은 ISO-8601(밀리초까지)입니다.
- 설정: `application.properties`(공통) + 프로필 `local`(각자 PC, compose. 프로필을 정하지 않으면 기본) · `test`(테스트) · `prod`(배포). 테스트 클래스에는 `test` 프로필을 붙입니다.
- WebSocket은 의존성만 있습니다. 설정·핸들러는 S7 때 만듭니다.

## 테스트

- 필수: 서버 판정(보상·투자·해금), 중복 방지(같은 요청 반복), 예외(상태 코드·오류 코드)에 걸린 코드. 나머지는 권장합니다.
- 통합 테스트에는 `@IntegrationTest`(앱 전체 + MockMvc + test 프로필 + Testcontainers)를 붙입니다. 모두 같은 애너테이션을 쓰면 컨테이너를 한 번만 띄웁니다. 웹 계층만 볼 때는 `@WebMvcTest`를 씁니다.
- 테스트마다 무엇을 확인하는지, 실패하면 무엇을 의심할지 주석으로 적습니다.
- 테스트는 Docker Desktop이 켜져 있어야 돕니다. PR 전에 `gradlew build`를 통과시킵니다.

## 작업 원칙

- 주장에는 근거를 붙입니다(실행 결과, `파일:줄`).
- "구현했다"는 빌드·테스트로 실행해 확인한 상태를 말합니다. 실행하지 않았으면 "작성함(미검증)"이라고 적습니다.

## 로컬 인프라

- `compose.yaml`은 로컬 개발 전용입니다. 운영 설정을 넣지 않습니다.
- `docker compose down -v`처럼 데이터를 지우는 명령은 사용자 확인 후 실행합니다.
