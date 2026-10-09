#!/usr/bin/env bash
# DESTROYS all local JobForge data volumes (Postgres, Redis, Kafka) and optionally restarts.
# Needed to re-run Postgres init scripts or after regenerating .env secrets.
# Usage: bash infrastructure/scripts/dev-reset.sh [--yes]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [[ "${1:-}" != "--yes" ]]; then
  read -r -p "This deletes ALL local JobForge data volumes. Continue? [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }
fi

docker compose down -v --remove-orphans
echo "Volumes removed. Start again with: bash infrastructure/scripts/dev-up.sh"
