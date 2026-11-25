#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENAPI_BASE_URL:-http://localhost:8080}"
OUTPUT="${OPENAPI_OUTPUT:-docs/openapi/openapi.v1.json}"
mkdir -p "$(dirname "$OUTPUT")"
curl --fail --silent --show-error --retry 20 --retry-connrefused --retry-delay 2 \
  "$BASE_URL/v3/api-docs" -o "$OUTPUT"
jq -S . "$OUTPUT" > "${OUTPUT}.tmp"
mv "${OUTPUT}.tmp" "$OUTPUT"
printf 'OpenAPI document exported to %s\n' "$OUTPUT"
