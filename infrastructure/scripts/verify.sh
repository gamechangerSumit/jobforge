#!/usr/bin/env bash
# Waits for health checks, then runs functional checks against the local infrastructure.
# Exit code 0 = everything OK. Usage: bash infrastructure/scripts/verify.sh   (VERIFY_TIMEOUT=seconds, default 180)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || { echo "ERROR: .env missing - run infrastructure/scripts/gen-secrets.sh" >&2; exit 1; }
set -a; source .env; set +a

TIMEOUT="${VERIFY_TIMEOUT:-180}"
fail=0
ok()  { printf '  [PASS] %s\n' "$1"; }
bad() { printf '  [FAIL] %s\n' "$1"; fail=1; }

container_of() { docker compose ps -a -q "$1" 2>/dev/null | head -n1; }

wait_healthy() {
  local svc="$1" end=$((SECONDS + TIMEOUT)) cid st
  while (( SECONDS < end )); do
    cid="$(container_of "$svc")"
    if [[ -n "$cid" ]]; then
      st="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)"
      [[ "$st" == "healthy" ]] && return 0
    fi
    sleep 3
  done
  return 1
}

wait_completed() {
  local svc="$1" end=$((SECONDS + TIMEOUT)) cid st code
  while (( SECONDS < end )); do
    cid="$(container_of "$svc")"
    if [[ -n "$cid" ]]; then
      st="$(docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || true)"
      code="$(docker inspect -f '{{.State.ExitCode}}' "$cid" 2>/dev/null || true)"
      [[ "$st" == "exited" && "$code" == "0" ]] && return 0
      [[ "$st" == "exited" && "$code" != "0" ]] && return 1
    fi
    sleep 3
  done
  return 1
}

psql_super() { docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "$1" 2>/dev/null; }

echo "== Health checks (timeout ${TIMEOUT}s each) =="
for s in postgres redis kafka mailpit; do
  if wait_healthy "$s"; then ok "$s healthy"; else bad "$s not healthy (docker compose logs $s)"; fi
done
if wait_completed kafka-init; then ok "kafka-init completed (exit 0)"; else bad "kafka-init did not complete (docker compose logs kafka-init)"; fi

echo "== Functional checks =="
# Postgres: schemas, extensions, roles, privilege isolation
[[ "$(psql_super "select string_agg(schema_name, ',' order by schema_name) from information_schema.schemata where schema_name in ('core','community','platform','ai')")" == "ai,community,core,platform" ]] \
  && ok "postgres schemas core/community/platform/ai exist" || bad "postgres schemas missing"
[[ "$(psql_super "select count(*) from pg_extension where extname in ('citext','pg_trgm','unaccent')")" == "3" ]] \
  && ok "postgres extensions citext/pg_trgm/unaccent" || bad "postgres extensions missing"
[[ "$(psql_super "select count(*) from pg_roles where rolname in ('jobforge_owner','jobforge_backend','jobforge_ai')")" == "3" ]] \
  && ok "postgres roles created" || bad "postgres roles missing"
[[ "$(psql_super "select has_schema_privilege('jobforge_backend','core','USAGE') and not has_schema_privilege('jobforge_backend','core','CREATE') and not has_schema_privilege('jobforge_backend','ai','USAGE') and has_schema_privilege('jobforge_ai','ai','USAGE') and not has_schema_privilege('jobforge_ai','core','USAGE')")" == "t" ]] \
  && ok "postgres privilege isolation (backend!=ai, no DDL for runtime roles)" || bad "postgres privilege isolation wrong"
for pair in "jobforge_backend:$DB_PASSWORD" "jobforge_ai:$AI_DB_PASSWORD" "jobforge_owner:$DB_MIGRATION_PASSWORD"; do
  u="${pair%%:*}"; p="${pair#*:}"
  [[ "$(docker compose exec -T -e PGPASSWORD="$p" postgres psql -h 127.0.0.1 -U "$u" -d "$POSTGRES_DB" -Atc 'select 1' 2>/dev/null)" == "1" ]] \
    && ok "postgres login as $u" || bad "postgres login as $u failed"
done

# Redis: ping + TTL round trip
[[ "$(docker compose exec -T redis sh -c 'redis-cli ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD" --no-auth-warning} ping' 2>/dev/null | tr -d '\r')" == "PONG" ]] \
  && ok "redis PING" || bad "redis PING failed"
[[ "$(docker compose exec -T redis sh -c 'redis-cli ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD" --no-auth-warning} SETEX jf:dev:verify:ping 10 ok >/dev/null && redis-cli ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD" --no-auth-warning} GET jf:dev:verify:ping' 2>/dev/null | tr -d '\r')" == "ok" ]] \
  && ok "redis SETEX/GET with TTL" || bad "redis SETEX/GET failed"

# Kafka: 8 domains x (topic + dlq) = 16 topics
n="$(docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --list 2>/dev/null | grep -c '^jobforge\.' || true)"
[[ "$n" == "16" ]] && ok "kafka topics created (16)" || bad "kafka topics: expected 16, found ${n:-0}"

# Mailpit
docker compose exec -T mailpit /mailpit readyz >/dev/null 2>&1 \
  && ok "mailpit ready (UI http://localhost:${MAILPIT_UI_HOST_PORT:-8025}, SMTP localhost:${MAILPIT_SMTP_HOST_PORT:-1025})" || bad "mailpit not ready"

echo
if (( fail )); then echo "RESULT: FAILED"; exit 1; else echo "RESULT: ALL CHECKS PASSED"; fi
