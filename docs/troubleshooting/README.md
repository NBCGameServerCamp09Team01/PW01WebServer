# 트러블슈팅 기록

문제를 만나면 아래 틀로 한 항목씩 남깁니다. 같은 문제를 다른 팀원이 다시 겪지 않게 하고, 발표의 트러블슈팅 자료로 씁니다.

## 틀

```markdown
### 제목 (날짜, 기록한 사람)

- 증상:
- 가설:
- 반증·확인:
- 원인:
- 조치:
- 배운 것:
```

## 목록

| 날짜 | 제목 |
|---|---|
| 2026-10-06 | [3306을 Windows의 MySQL 서비스가 쓰고 있어 compose MySQL과 부딪힘](#3306을-windows의-mysql-서비스가-쓰고-있어-compose-mysql과-부딪힘-2026-10-06-sang-hyun-kim) |
| 2026-10-06 | [프로필 없이 실행해 DataSource url을 찾지 못함](#프로필-없이-실행해-datasource-url을-찾지-못함-2026-10-06-sang-hyun-kim) |
| 2026-10-06 | [앱이 compose MySQL이 아니라 PC의 MySQL에 붙어 Access denied](#앱이-compose-mysql이-아니라-pc의-mysql에-붙어-access-denied-2026-10-06-sang-hyun-kim) |

### 3306을 Windows의 MySQL 서비스가 쓰고 있어 compose MySQL과 부딪힘 (2026-10-06, Sang-Hyun-Kim)

- 증상: 초기 설정 전 환경 점검에서 발견했다. compose MySQL은 `127.0.0.1:3306`을 쓰려는데 그 포트가 이미 열려 있었다. 그대로 두면 compose MySQL이 포트를 잡지 못하거나, 앱이 다른 MySQL에 붙는다.
- 가설: 다른 프로그램이 3306을 쓰고 있다.
- 반증·확인: `netstat -ano | findstr :3306`으로 PID를 찾고 `tasklist /FI "PID eq <PID>"`로 보니 `mysqld.exe`였다. `sc qc MySQL80`으로 보니 PC에 설치된 MySQL 8.0 서비스(자동 시작)였다.
- 원인: Windows 서비스로 설치된 MySQL(MySQL80)이 부팅 때마다 3306을 연다.
- 조치: 서비스는 그대로 두고 `.env`에 `MYSQL_PORT=3307`을 적었다. compose(`${MYSQL_PORT:-3306}`)와 local 프로필(`${MYSQL_PORT:3306}`)이 같은 변수를 읽으므로, IntelliJ 실행 구성의 환경 변수에도 `MYSQL_PORT=3307`을 넣었다.
- 배운 것: 새 PC에서는 `docker compose up` 전에 3306·6379 사용 여부를 확인한다. 포트는 코드가 아니라 `.env`에서 바꿀 수 있게 열어 둔다.

### 프로필 없이 실행해 DataSource url을 찾지 못함 (2026-10-06, Sang-Hyun-Kim)

- 증상: IntelliJ에서 처음 실행하자 `Failed to configure a DataSource: 'url' attribute is not specified`로 기동이 실패했다. 로그 둘째 줄이 `No active profile set, falling back to 1 default profile: "default"`였다.
- 가설: DB 접속 설정이 들어 있는 `application-local.properties`를 읽지 않았다.
- 반증·확인: 접속 주소는 local 프로필 파일에만 있고, 로그상 켜진 프로필이 없었다.
- 원인: 실행 구성에 프로필(`local`)이 전달되지 않았다.
- 조치: `application.properties`에 `spring.profiles.default=local`을 넣어, 프로필을 정하지 않으면 local로 뜨게 했다. 테스트 클래스에는 `test` 프로필을 직접 붙인다.
- 배운 것: 기동 로그의 "profile is active" 줄을 먼저 본다.

### 앱이 compose MySQL이 아니라 PC의 MySQL에 붙어 Access denied (2026-10-06, Sang-Hyun-Kim)

- 증상: local 프로필로 실행하자 Flyway가 `Access denied for user 'pw01'@'localhost' (using password: YES)`로 실패했다.
- 가설: 앱이 비밀번호 설정을 찾지 못한다.
- 반증·확인:
  - `using password: YES`이므로 비밀번호는 전달됐다. 못 찾았다면 `Could not resolve placeholder 'MYSQL_PASSWORD'`가 난다.
  - 컨테이너 안에서 컨테이너의 환경 변수로 로그인하면 성공했다(비밀번호 값은 출력되지 않음): `docker compose exec -T mysql sh -c 'mysql -upw01 -p"$MYSQL_PASSWORD" -e "SELECT 1" pw01'`
  - 오류의 호스트가 `localhost`였다. compose MySQL이었다면 Docker 네트워크 주소(172.x.x.x)로 찍힌다.
- 원인: 실행 구성에 `MYSQL_PORT=3307`이 빠져 local 프로필의 기본값 3306으로 접속했고, PC의 MySQL80 서비스에 붙었다.
- 조치: 실행 구성의 환경 변수에 `MYSQL_PORT=3307`을 넣었다. health UP, 예시 API 201을 확인했다.
- 배운 것: `Access denied for user '사용자'@'호스트'`의 호스트를 보면 어느 서버에 붙었는지 알 수 있다. compose와 앱은 같은 포트 값을 써야 한다(`.env`와 실행 구성).
