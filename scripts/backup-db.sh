#!/usr/bin/env bash
# 로컬에서 임시로 백업 받을 때 사용. CI 와 동일한 dump 포맷.
#
# 사용:
#   DATABASE_URL=postgres://... ./scripts/backup-db.sh
#   → ./homefit-<timestamp>.dump 생성

set -euo pipefail

if [ -z "${DATABASE_URL:-}" ]; then
  echo "DATABASE_URL 환경변수 필요" >&2
  exit 1
fi

STAMP=$(date -u +"%Y%m%dT%H%M%SZ")
OUT="homefit-${STAMP}.dump"

echo "Dumping → $OUT"
pg_dump --format=custom --no-owner --no-acl --file "$OUT" "$DATABASE_URL"

ls -lh "$OUT"
echo
echo "복구:"
echo "  pg_restore --no-owner --no-acl -d <target_db_url> $OUT"
