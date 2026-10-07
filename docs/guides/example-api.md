# 예시 API 따라 하기 (학습용)

> **학습용 예시입니다. 실제 기능이 아닙니다.** 첫 실제 API(S1, 로그인)가 병합되면 예시 코드·테이블과 이 문서를 지웁니다(맨 아래 "지우는 법").
> 명세: 루트 `docs/contracts/example-api.md` · 공통 규칙(경로·시각·오류 응답): 루트 `docs/contracts/README.md`
> 예시는 경로 규칙이 바뀌기 전(10/7)에 만들어 옛 경로 `/api/v1/examples`를 씁니다. 새 API는 기능 이름부터 씁니다(예: `/auth/login`).

요청 하나가 **Controller → Service → Repository → DB**를 지나 응답이 되기까지를 파일 순서대로 보여 줍니다. 새 API를 만들 때 이 순서를 그대로 따라 하면 됩니다. 코드 주석은 짧게 두고, "왜 이렇게 했나"는 이 문서에 모았습니다.

## 1. 직접 불러 보기

1. compose로 MySQL·Redis를 띄우고 앱을 `local` 프로필로 실행합니다(README "처음 받기").
2. Swagger UI(http://localhost:8080/swagger-ui/index.html)에서 `POST /api/v1/examples`를 실행하거나, IntelliJ HTTP Client(`.http` 파일)로 보냅니다.

```http
POST http://localhost:8080/api/v1/examples
Content-Type: application/json

{"name": "first-example", "description": "학습용 예시입니다."}
```

- 201과 `Location: /api/v1/examples/{id}`가 오고, 본문은 명세의 예시(`examples/example-create-response.json`)와 같은 모양입니다.
- 같은 요청을 한 번 더 보내면 409 `EXAMPLE_NAME_DUPLICATED`, `name`을 비우면 400 `VALIDATION_FAILED`, 모르는 필드를 넣으면 400 `INVALID_REQUEST_BODY`가 옵니다.
- Swagger 아래쪽 "Responses" 표는 springdoc이 코드에서 추정한 것이라 201 대신 200으로 보이고, 실제 응답(201) 옆에는 "Undocumented"가 붙습니다. 응답 코드의 기준은 명세입니다.

## 2. 요청 하나의 길 (`POST /api/v1/examples`)

경로는 `src/main/java/com/pw01/webserver/` 기준입니다.

| 순서 | 파일 | 하는 일 | 지키는 규칙 |
|---|---|---|---|
| 1 | `example/controller/ExampleController` | 경로를 받고 `@Valid`로 요청 DTO를 검증한 뒤 Service를 부른다. 201 + `Location` | Controller는 Repository를 부르지 않는다 |
| 2 | `example/dto/ExampleCreateRequest` | 요청 record + Bean Validation(`@NotBlank`, `@Size`) | 형식 검증은 DTO, 게임 규칙 검증은 Service |
| 3 | `example/service/ExampleService` | 트랜잭션, 이름 중복 판정, 저장 | 거절은 상태별 예외 + 오류 코드(`ConflictException("EXAMPLE_NAME_DUPLICATED", …)`) |
| 4 | `example/repository/ExampleRepository` | `JpaRepository` + `existsByName` | 메서드 이름으로 쿼리를 만든다 |
| 5 | `example/entity/Example`, `common/entity/BaseEntity` | 테이블 한 행. 생성·수정 시각은 자동 | 엔티티는 API 밖으로 나가지 않는다 |
| 6 | `src/main/resources/db/migration/V1__example.sql` | 테이블과 유일 제약 | 스키마는 마이그레이션으로만 바꾼다 |
| 7 | `example/dto/ExampleResponse.from(entity)` | 엔티티 → 응답 record | 변환은 응답 DTO 한 곳에 모은다 |
| 8 | `common/error/GlobalExceptionHandler` | 예외 → `{code, message, path, errors}` | 오류 본문은 이 한 곳에서 만든다 |
| 테스트 | `src/test/java/.../example/ExampleApiTest` | 성공·400·404·409를 실제 MySQL로 확인 | 서버 판정·중복 방지·예외는 테스트 필수 |

## 3. 파트별 따라 하기

### b — UE 연동

- 요청·응답 JSON은 명세(루트 `docs/contracts/`)와 예시 JSON(`docs/contracts/examples/`)이 기준입니다. UE 구조체는 이 JSON에 맞춥니다.
- UE `FJsonObjectConverter`는 UPROPERTY 이름의 첫 글자를 소문자로 바꿔 JSON 키를 만듭니다(`Name` → `name`, `CreatedAt` → `createdAt`). 서버 DTO의 필드 이름(camelCase)과 맞습니다.
- 서버는 **요청**에 모르는 필드가 있으면 400 `INVALID_REQUEST_BODY`로 거절합니다. UE 구조체에 서버가 모르는 칸이 있으면 요청이 실패합니다. 반대로 **응답**에 UE가 모르는 칸이 있으면 무시하면 됩니다.
- 오류는 HTTP 상태와 `code`로 판단합니다. 화면 문구는 `code`로 고릅니다(오류 코드 → 화면 문구 표는 b·c가 정함).
- `id`는 64비트 정수(`int64`), `createdAt`은 UTC ISO-8601(`…Z`, 밀리초까지)입니다. `FDateTime::ParseIso8601`로 읽히는지 확인해 주세요.

### c — 서버 API

새 API를 만드는 순서입니다.

1. 루트 `docs/contracts/`에 명세(엔드포인트·요청·응답·오류 코드)를 먼저 쓰고 리뷰를 받습니다.
2. `<기능>/dto/`에 요청 record + Validation, 응답 record + `from(entity)`를 만듭니다.
3. `<기능>/controller/`에 `/<기능>/…` 경로(`/api`·버전 없음)와 `@Valid @RequestBody`를 둡니다.
4. `<기능>/service/`에 판정과 트랜잭션을 둡니다. 거절은 상태별 예외(`NotFoundException`, `ConflictException`, `ForbiddenException`, `InvalidRequestException`, `ServiceUnavailableException`)에 명세의 오류 코드를 담아 던집니다.
5. 다른 기능의 데이터가 필요하면 그 기능의 Service를 부릅니다. 다른 기능의 Repository를 직접 부르지 않습니다.
6. `@IntegrationTest`로 성공과 오류 코드(400·404·409 …)를 확인합니다. 서버 판정·중복 방지·예외는 필수입니다.

### d — DB

1. `db/migration/V{다음 번호}__{설명}.sql`로 테이블을 만듭니다. 칼럼 이름은 snake_case입니다(엔티티 필드 `createdAt`은 칼럼 `created_at`으로 자동 대응). 시각 칼럼은 `created_at`·`updated_at` `DATETIME(3) NOT NULL`.
2. 엔티티는 `BaseEntity`를 상속하고, `@Table(uniqueConstraints = …)`에 마이그레이션과 같은 제약 이름을 적습니다. 어떤 제약이 있는지 코드에서도 보이게 하려는 것입니다.
3. 유일 제약은 "같은 요청 반복"을 막는 마지막 방어입니다. 같은 이름 요청 두 개가 동시에 오면 `existsByName` 확인을 함께 통과할 수 있는데, 그때는 DB 제약 위반을 `ConflictException`으로 바꿔 409가 됩니다(`ExampleService`).
4. 인덱스는 조회 패턴을 보고 마이그레이션에 만들고, README "제약·인덱스와 이유" 표에 이유를 적습니다.
5. `dev`에 병합된 마이그레이션 파일은 고치지 않습니다. 바꿀 것은 새 파일로 더합니다. 이미 적용한 팀원 DB에서 검사값이 달라 기동이 실패하기 때문입니다.
6. 앱이 뜰 때 Hibernate가 엔티티와 테이블을 비교합니다(`ddl-auto=validate`). 맞지 않으면 기동이 실패하고, 스모크 테스트가 먼저 잡습니다.

## 4. 지우는 법 (S1 병합 때)

1. 코드: `src/main/java/com/pw01/webserver/example/`, `src/test/java/com/pw01/webserver/example/`
2. DB: `V1__example.sql`은 그대로 두고 `V{다음 번호}__drop_example.sql`(`DROP TABLE example;`)을 더합니다.
3. 웹서버 문서: 이 파일, `docs/README.md`의 행, README의 예시 언급과 "제약·인덱스와 이유" 표의 `example` 행
4. 루트(PW01): `docs/contracts/example-api.md`, `docs/contracts/examples/example-*.json`, 목록 두 곳(`docs/contracts/README.md`, `docs/README.md`)
5. 공통 오류 틀의 테스트(`common/error/GlobalExceptionHandlerTest`)는 예시와 상관없어 남습니다.
