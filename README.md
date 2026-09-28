# PW01WebServer

ProjectWarrior 게임의 Spring Boot 백엔드 저장소입니다. 지금은 초기 파일만 있고, Spring Boot 프로젝트는 아직 만들지 않았습니다.

- 작업 공간 루트: https://github.com/NBCGameServerCamp09Team01/PW01 (이 폴더는 루트 안의 `PW01\PW01WebServer\` 에 둡니다)
- 게임과의 약속(API, Redis 키): 루트 `docs/contracts/` — **코드보다 명세를 먼저 고칩니다**
- 작업 규칙: [AGENTS.md](AGENTS.md), 루트 `docs/git-workflow.md`

## 받기

루트에서 `tools\setup.bat web` 을 실행하거나, `PW01\` 안에서:

```bat
git clone https://github.com/NBCGameServerCamp09Team01/PW01WebServer.git
```

git 명령은 이 폴더(`PW01\PW01WebServer\`) 안에서 실행합니다.

## 로컬 Redis·DB 실행

[Docker Desktop](https://www.docker.com/products/docker-desktop/)이 필요합니다.

```bat
cd PW01\PW01WebServer
copy .env.example .env
:: .env 를 열어 비밀번호 값을 채웁니다 (.env 는 커밋되지 않습니다)
docker compose up -d
docker compose ps
```

- MySQL: `127.0.0.1:3306`, Redis: `127.0.0.1:6379` (포트는 `.env`에서 바꿀 수 있음)
- 멈추기: `docker compose down` / 데이터까지 지우기: `docker compose down -v`

## 구조

```
PW01WebServer/
├─ README.md
├─ AGENTS.md                            ← 에이전트 작업 규칙
├─ CLAUDE.md                            ← @AGENTS.md
├─ .github/pull_request_template.md
├─ compose.yaml                         ← 로컬 Redis, DB
├─ .env.example                         ← 환경변수 목록 (값 비움)
└─ docs/                                ← 웹서버 문서
```

## 문서

- [docs/README.md](docs/README.md) — 웹서버 문서 지도
- [docs/overview.md](docs/overview.md) — 웹서버 구조
