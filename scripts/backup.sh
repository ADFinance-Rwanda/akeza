#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

user="${DATABASE_USERNAME:-tenant}"
db="tenant_management"
stamp="$(date +%Y%m%d-%H%M%S)"
mkdir -p backups
out="backups/tenant_management-${stamp}.sql"

echo "Dumping ${db} from Compose service db as user ${user}"
docker compose exec -T db pg_dump -U "$user" -d "$db" --no-owner --no-acl --clean --if-exists > "$out"
echo "Backup written to ${out}"
echo "$out"
