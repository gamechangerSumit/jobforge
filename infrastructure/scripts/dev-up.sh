#!/usr/bin/env bash
# Starts the local infrastructure (default profile) and verifies it.
# Usage: bash infrastructure/scripts/dev-up.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

command -v docker >/dev/null 2>&1 || { echo "ERROR: docker not found. Install Docker Engine/Desktop (free)." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "ERROR: 'docker compose' (v2) not available." >&2; exit 1; }

if [[ ! -f .env ]]; then
  echo "No .env found - generating one."
  bash infrastructure/scripts/gen-secrets.sh
fi
if grep -qE '^[A-Z0-9_]+=CHANGE_ME$' .env; then
  echo "ERROR: .env still contains CHANGE_ME placeholders. Run: bash infrastructure/scripts/gen-secrets.sh --force" >&2
  exit 1
fi

docker compose up -d
bash infrastructure/scripts/verify.sh
