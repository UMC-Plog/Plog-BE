#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

if [ ! -f docker-compose.api-test.yml ]; then
  echo "docker-compose.api-test.yml이 없어 정리할 로컬 테스트 서버가 없습니다."
  exit 0
fi

docker compose --env-file .env.api-test -f docker-compose.api-test.yml \
  down --volumes --remove-orphans
