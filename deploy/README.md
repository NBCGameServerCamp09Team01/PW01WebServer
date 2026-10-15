# 배포(EC2) — 스크립트와 자동 배포

배포 서버(EC2, Amazon Linux 2023)에서 이미지를 바꿔 띄우는 스크립트와, GitHub Actions에서 그 스크립트를 부르는 방법입니다. 흐름도: 루트 `docs/flows/deploy.md`.

| 파일 | 하는 일 |
|---|---|
| `deploy.sh` | 서버의 `/opt/pw01/deploy.sh` 원본. ECR 이미지 받기 → Parameter Store 값 읽기 → 컨테이너 바꾸기 → health 기다리기 → 실패면 이전 컨테이너로 되돌림 |
| `../.github/workflows/deploy-ec2.yml` | 이미지 workflow가 `dev`에서 성공하면 SSM Run Command로 서버마다 `deploy.sh dev-<커밋>`을 부름. 손 배포·되돌림도 |

## 서버에 스크립트 놓기(바꿀 때마다 한 번, 사람이)

`deploy.sh`를 고쳐 병합하면 서버 파일도 바꿔야 합니다. workflow는 서버에 있는 파일을 부를 뿐 복사하지 않습니다.

1. EC2 콘솔 → `pw01-app-1` → 연결 → Session Manager.
2. 지금 파일을 남기고 새 내용을 씁니다.

```bash
sudo cp /opt/pw01/deploy.sh /opt/pw01/deploy.sh.bak-$(date +%m%d)
sudo tee /opt/pw01/deploy.sh > /dev/null <<'EOF'
(이 폴더 deploy.sh 내용 전체)
EOF
sudo chmod 750 /opt/pw01/deploy.sh
sudo bash -n /opt/pw01/deploy.sh && echo "문법 OK"
```

- 줄 끝은 LF여야 합니다(`.gitattributes`가 `*.sh`를 LF로 고정). Windows 편집기에서 CRLF로 붙이면 `$'\r': command not found`가 납니다.
- 사용자 데이터(새 서버를 만들 때 쓰는 스크립트)에 같은 내용이 들어 있으면 같이 고칩니다.

## 배포하기

| 하고 싶은 것 | 방법 |
|---|---|
| 자동(병합하면 바로) | 저장소 변수 `AUTO_DEPLOY=true`. `dev` 병합 → Build and Push Image (ECR) → Deploy to EC2 |
| 자동 배포 끄기(시험 중) | `AUTO_DEPLOY=false`(또는 변수 삭제). 이미지는 계속 올라갑니다 |
| 손 배포 | Actions → Deploy to EC2 → Run workflow(브랜치 `dev`) → 태그 `dev-<커밋 40자>` |
| 되돌리기 | 같은 화면에서 태그 칸에 `rollback` → 서버의 직전 태그(`/opt/pw01/previous-tag`)로 |
| 서버에서 직접 | Session Manager → `sudo /opt/pw01/deploy.sh dev-<커밋 40자>` 또는 `sudo /opt/pw01/deploy.sh rollback` |
| 지금 태그 | `sudo cat /opt/pw01/current-tag`, 서버 안 `curl -s localhost:8081/actuator/info` → `app.commit` |

- 서버가 한 대인 동안 배포마다 약 20초 503이 납니다. 시험 중에는 배포하지 않습니다.
- 데이터베이스 마이그레이션(Flyway)은 새 이미지가 뜰 때 적용되고, 되돌려도 DB는 되돌아가지 않습니다.

## 저장소 변수

| 이름 | 예 | 없으면 |
|---|---|---|
| `DEPLOY_ROLE_ARN` | `arn:aws:iam::<계정ID>:role/pw01-gha-deploy` | 배포 workflow 전체를 건너뜀 |
| `AUTO_DEPLOY` | `true` | 자동 배포 안 함(손 배포만) |
| `DEPLOY_CHECK_URL` | `http://<로드 밸런서 주소>/health` | 밖에서 확인을 건너뜀 |
| `AWS_REGION`, `ECR_REPOSITORY` | 이미지 workflow와 같음 | — |

## 역할 `pw01-gha-deploy`가 할 수 있는 것

- 신뢰: GitHub OIDC, `aud` = `sts.amazonaws.com`, `sub` = 이 저장소 `dev` 브랜치(불변 주체 모양 — 루트 `docs/flows/deploy.md`).
- 권한: 태그 `role=pw01-app` 서버에 `AWS-RunShellScript` 명령 보내기, 명령 결과 읽기, 서버 찾기(`ec2:DescribeInstances`), ECR 태그 확인(`ecr:DescribeImages`). 그 밖은 없음.
- 비밀값(DB·Redis 비밀번호)은 서버가 Parameter Store에서 직접 읽습니다. workflow에도 이 역할에도 없습니다.
