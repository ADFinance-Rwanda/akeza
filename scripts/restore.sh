#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <backup.sql>" >&2
  exit 1
fi
if [[ ! -f "$1" ]]; then
  echo "Backup file not found: $1" >&2
  exit 1
fi

user="${DATABASE_USERNAME:-tenant}"
db="tenant_management"

echo "Stopping app and worker so connections are released"
docker compose stop app worker

echo "Terminating leftover sessions on ${db}"
docker compose exec -T db psql -U "$user" -d postgres -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${db}' AND pid <> pg_backend_pid();"

echo "Dropping and recreating ${db}"
docker compose exec -T db psql -U "$user" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS ${db};"
docker compose exec -T db psql -U "$user" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE ${db};"

echo "Restoring $1"
docker compose exec -T db psql -U "$user" -d "$db" -v ON_ERROR_STOP=1 < "$1"

echo "Starting app and worker"
docker compose start app worker
echo "Restore finished. Confirm GET /ready after the app is healthy."
