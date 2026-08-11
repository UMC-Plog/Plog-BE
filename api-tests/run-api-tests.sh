#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

if [ -f .env.api-test ]; then
  set -a
  . ./.env.api-test
  set +a
fi

PLOG_API_URL=${PLOG_API_URL:-http://localhost:8080}
PLOG_API_HEALTH_URL=${PLOG_API_HEALTH_URL:-$PLOG_API_URL/v3/api-docs}

if [ -f docker-compose.api-test.yml ]; then
  docker compose --env-file .env.api-test -f docker-compose.api-test.yml up -d --build
fi

attempt=0
until curl --fail --silent --output /dev/null "$PLOG_API_HEALTH_URL"; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "API 서버가 60초 안에 준비되지 않았습니다: $PLOG_API_HEALTH_URL" >&2
    exit 1
  fi
  sleep 1
done

./gradlew apiTest --console=plain
