#!/usr/bin/env bash
# PW01 웹서버 배포(EC2 한 대): 이미지 받기 → 설정 값 읽기 → 바꿔 띄우기 → health 기다리기 → 실패면 이전 컨테이너로 되돌림
# 사용: sudo /opt/pw01/deploy.sh <이미지 태그>
#   예) sudo /opt/pw01/deploy.sh dev-d2c6a0372b7b74581ae3fe108b9f5a6fbbb2bda1
#   되돌리기) sudo /opt/pw01/deploy.sh rollback      (직전 태그 /opt/pw01/previous-tag로)
#   지금 태그) sudo cat /opt/pw01/current-tag
# 규칙: 비밀값을 화면·로그에 찍지 않는다(set -x 금지, 값을 echo하지 않음). 이 파일에는 비밀이 없다.
# 태그 파일은 root만 읽는다(umask 077) → "$(cat …)"를 sudo 밖에서 쓰면 Permission denied(10/14 겪음). 그래서 rollback을 둔다.
set -euo pipefail

TAG="${1:?사용법: deploy.sh <이미지 태그(dev-<커밋 40자>)> | rollback}"
if [ "$TAG" = "rollback" ]; then
  TAG="$(cat /opt/pw01/previous-tag 2>/dev/null || true)"
  if [ -z "$TAG" ]; then
    echo "[deploy] 되돌릴 직전 태그가 없음(/opt/pw01/previous-tag)"; exit 1
  fi
  echo "[deploy] rollback → ${TAG}"
fi
REGION="ap-northeast-2"
REPO="pw01/webserver"
PARAM_PATH="/pw01/prod"
NAME="pw01-webserver"
STATE_DIR="/opt/pw01"
ENV_DIR="/run/pw01"            # /run은 메모리(tmpfs) — 재부팅하면 사라지고 디스크에 쓰이지 않는다
ENV_FILE="${ENV_DIR}/app.env"
HEALTH_URL="http://localhost:8081/actuator/health"
HEALTH_TRIES=60                # 3초 × 60 = 최대 3분(첫 기동은 Flyway가 표를 만든다)
MEMORY_LIMIT="1536m"           # t3.small 2GiB 중 컨테이너 몫. JVM은 이 값의 75%를 힙으로 쓴다(Dockerfile)
MIN_VALUES=8                   # Parameter Store 7개 + SPRING_PROFILES_ACTIVE

log() { echo "[deploy $(date -u +%H:%M:%SZ)] $*"; }

mkdir -p "$STATE_DIR"
install -d -m 700 "$ENV_DIR"
trap 'rm -f "$ENV_FILE"' EXIT  # 성공·실패와 상관없이 env 파일을 지운다

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE="${REGISTRY}/${REPO}:${TAG}"

log "1/6 ECR 로그인·이미지 받기: ${REPO}:${TAG}"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null
docker pull -q "$IMAGE" >/dev/null

log "2/6 설정 값 읽기: ${PARAM_PATH}/* (값은 출력하지 않음)"
umask 077
aws ssm get-parameters-by-path --region "$REGION" --path "$PARAM_PATH" --with-decryption \
    --query 'Parameters[*].[Name,Value]' --output text \
  | while IFS=$'\t' read -r name value; do printf '%s=%s\n' "${name##*/}" "$value"; done \
  > "$ENV_FILE"
printf 'SPRING_PROFILES_ACTIVE=prod\n' >> "$ENV_FILE"
COUNT="$(grep -c '=' "$ENV_FILE" || true)"
log "    값 ${COUNT}개"
if [ "$COUNT" -lt "$MIN_VALUES" ]; then
  log "설정 값이 모자람(${COUNT} < ${MIN_VALUES}). 배포 중단 — 지금 컨테이너는 그대로"
  exit 1
fi

PREV_TAG="$(cat "${STATE_DIR}/current-tag" 2>/dev/null || true)"

log "3/6 지금 컨테이너 멈춤(되돌림용으로 ${NAME}-old 이름으로 남김)"
docker rm -f "${NAME}-old" >/dev/null 2>&1 || true
if docker inspect "$NAME" >/dev/null 2>&1; then
  docker stop "$NAME" >/dev/null
  docker rename "$NAME" "${NAME}-old"
fi

log "4/6 새 컨테이너 시작"
docker run -d --name "$NAME" --restart unless-stopped \
  --env-file "$ENV_FILE" --memory "$MEMORY_LIMIT" \
  -p 8080:8080 -p 8081:8081 \
  --log-opt max-size=10m --log-opt max-file=3 \
  "$IMAGE" >/dev/null
rm -f "$ENV_FILE"

log "5/6 health 기다리기(최대 $((HEALTH_TRIES * 3))초)"
for i in $(seq 1 "$HEALTH_TRIES"); do
  # 503(DOWN)이면 curl -f가 실패하므로 본문의 UP 글자에 속지 않는다
  if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    log "    UP (${i}번째 확인)"
    echo "$TAG" > "${STATE_DIR}/current-tag"
    if [ -n "$PREV_TAG" ] && [ "$PREV_TAG" != "$TAG" ]; then
      echo "$PREV_TAG" > "${STATE_DIR}/previous-tag"
    fi
    docker rm -f "${NAME}-old" >/dev/null 2>&1 || true
    log "6/6 끝: ${TAG}"
    exit 0
  fi
  sleep 3
done

log "health가 UP이 아님 → 되돌림. 새 컨테이너 마지막 로그 40줄:"
docker logs --tail 40 "$NAME" 2>&1 | sed 's/^/    /' || true
docker rm -f "$NAME" >/dev/null 2>&1 || true
if docker inspect "${NAME}-old" >/dev/null 2>&1; then
  docker rename "${NAME}-old" "$NAME"
  docker start "$NAME" >/dev/null
  log "이전 컨테이너(${PREV_TAG:-태그 기록 없음})로 되돌림"
else
  log "되돌릴 이전 컨테이너가 없음(첫 배포) — 위 로그로 원인을 본다"
fi
exit 1
