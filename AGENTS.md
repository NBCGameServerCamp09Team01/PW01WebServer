# AGENTS.md — PW01WebServer 작업 규칙 (초안)

이 폴더 작업에서는 이 파일이 루트 `AGENTS.md`보다 우선합니다. 여기서 다루지 않는 부분(git, 비밀값, 확인 절차)은 루트 `AGENTS.md`를 따릅니다.

## 범위

- Spring Boot 백엔드. 게임(ProjectWarrior)의 DS·클라이언트가 호출하는 API와 Redis·DB 연동을 담당합니다.
- git 명령은 이 폴더(`PW01WebServer/`) 안에서만 실행합니다. 루트 저장소에 이 폴더 파일을 커밋하지 않습니다.

## 비밀값

- DB 비밀번호, Redis 비밀번호, API 키, 토큰은 **환경변수**로만 받습니다. `application.yml`/`application.properties`에는 `${DB_PASSWORD}` 같은 참조만 적습니다.
- 로컬 값은 `.env`에 둡니다. `.env`는 커밋하지 않으며, 새 변수를 쓰면 `.env.example`에 **이름만** 추가합니다.
- 비밀값을 로그로 출력하지 않습니다. 요청·응답 로그에서 인증 헤더와 토큰은 가립니다.

## API 변경

- API나 Redis 키를 추가·변경할 때는 **루트 `docs/contracts/`의 명세를 먼저 고치고** PR 리뷰를 받은 뒤 코드를 바꿉니다.
- 명세와 코드가 다르면 명세가 기준입니다. 코드가 명세와 다르게 동작해야 한다면 명세를 고치는 PR부터 올립니다.
- 호환되지 않는 변경(필드 삭제·이름 변경)은 게임 담당자와 합의 없이 하지 않습니다.

## 코드 (Spring Boot 프로젝트 생성 후 보완)

- 빌드 도구·Java 버전·패키지 구조: TBD (프로젝트 생성 시 여기에 적기)
- 테스트: 변경한 API에는 테스트를 추가하고, PR 전에 전체 테스트를 실행합니다.
- 설정은 프로필(`local`, `dev`, `prod`)로 나눕니다. 로컬은 `compose.yaml`의 Redis·DB를 씁니다.

## 로컬 인프라

- `compose.yaml`은 로컬 개발 전용입니다. 운영 설정을 넣지 않습니다.
- `docker compose down -v`처럼 데이터를 지우는 명령은 사용자 확인 후 실행합니다.
