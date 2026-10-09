#!/usr/bin/env bash
# Creates ./.env from ./.env.example, replacing every KEY=CHANGE_ME with a random local-only secret.
# Usage: bash infrastructure/scripts/gen-secrets.sh [--force]
# .env is git-ignored. Never commit it. These secrets are for LOCAL development only.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXAMPLE="$ROOT/.env.example"
TARGET="$ROOT/.env"
FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

[[ -f "$EXAMPLE" ]] || { echo "ERROR: $EXAMPLE not found" >&2; exit 1; }
if [[ -f "$TARGET" && $FORCE -eq 0 ]]; then
  echo ".env already exists - leaving it untouched (use --force to regenerate; this changes DB passwords, so run dev-reset.sh afterwards)."
  exit 0
fi

rand_hex() {  # $1 = number of random bytes -> 2*N hex chars
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$1"
  else
    { LC_ALL=C tr -dc 'a-f0-9' </dev/urandom | head -c $(( $1 * 2 )); } || true
  fi
}

umask 077
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^([A-Z0-9_]+)=CHANGE_ME$ ]]; then
    key="${BASH_REMATCH[1]}"
    case "$key" in
      JWT_SECRET|AI_INTERNAL_TOKEN) val="$(rand_hex 32)" ;;
      DEV_SEED_PASSWORD)            val="Dev-$(rand_hex 8)1" ;;   # satisfies password policy (letters+digit, 10-64)
      *)                            val="$(rand_hex 24)" ;;
    esac
    printf '%s=%s\n' "$key" "$val"
  else
    printf '%s\n' "$line"
  fi
done < "$EXAMPLE" > "$tmp"

mv "$tmp" "$TARGET"
chmod 600 "$TARGET"
trap - EXIT
echo "Created $TARGET (random local secrets, mode 600)."
