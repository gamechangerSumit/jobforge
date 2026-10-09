# Status — Dev 3 (ai / infra lane)

_Last updated: 2026-10-01 · Branch: feature/infra_

## Done (Phase 0 infra foundation)
- `docker-compose.yml`: Postgres 16, Redis 7, Kafka (KRaft, dual listener), kafka-init, Mailpit; health checks; loopback-only ports; memory limits.
- `infrastructure/postgres/init/01..03`: extensions, roles, schemas, default privileges.
- `infrastructure/kafka/create-topics.sh`: 16 contract topics.
- `.env.example` (placeholders only, `AI_PROVIDER=local`, no Claude credentials required), `.gitignore`, `.gitattributes`.
- Scripts: `gen-secrets.sh`, `dev-up.sh`, `dev-reset.sh`, `verify.sh`. `infrastructure/README.md`.

## Not verified yet
- Real `docker compose up` run and image tag availability (`apache/kafka:3.8.0`, `axllent/mailpit:latest`) — run `bash infrastructure/scripts/dev-up.sh`.

## In progress / next
- Phase 0 remainder: `infra-ci.yml` (compose config, hadolint, shellcheck), CODEOWNERS/PR template, CI skeletons for other lanes' paths (separate small PRs).

## Blocked / requests
- REQ-20261001-db-bootstrap-and-kafka-listeners (Dev 1: Flyway config + audit_logs REVOKE).

## Revision 8 (unverified, nothing built or run)
- Done: `.github/workflows/integration.yml` (compose app-profile smoke: public /jobs 200, facets/skills/companies public, protected routes 401, frontend /jobs 200) and `release.yml` (tag v*.*.* -> GHCR images with SBOM + provenance); `infra-ci.yml` runs hadolint on backend, frontend and ai-service Dockerfiles; Grafana dashboard provisioning (`infrastructure/monitoring/grafana-dashboards.yml`, `dashboards/jobforge-backend.json`) mounted in the monitoring profile.
- Not done: Playwright job in integration.yml, MinIO S3 adapter, ai-service pipeline (Phase 3), ADR for micrometer-registry-prometheus, contract-change PR for REQ-20261008/09/10.
