# JobForge completion tracker

_Last updated: 2026-11-22. Working branch: `feature/jobforge-completion-v2`._

## Integration baseline

The working branch is based on `develop`, not `main`. The earlier `feature/jobforge-completion` branch was created from `main` and is stale; continue only on `feature/jobforge-completion-v2`.

Merged into this branch:
- PR #8: `feature/session-6-ai-integration` — AI service pipeline, local template provider, internal API, quota persistence, migrations, schema validation and tests. Merge commit: `57f4b8f9bd5cdd6e2d4a0864a99ecbc56d465dfb`.
- PR #9: `feature/session-7-notifications` — notification SSE stream, preference handling, email outbox dispatcher, frontend notification updates and cursor test. Merge commit: `79532d4da67966c7b926f420508c3c587510c0fe`.

Already present on `develop` at branch creation:
- Interview module (backend + frontend).
- Reports and moderation (backend + frontend).
- Community (backend + frontend).
- Recruiter/admin analytics (backend + frontend).

All prior feature work is static/unverified. No Maven, pnpm, Docker, application server, or test suite has been run as part of this completion effort. Do not label any test as passed until it has actually run.

## Remaining implementation

### P0 — AI public gateway (backend)
- Implement authenticated public `/api/v1/ai/**` endpoints.
- Proxy to internal ai-service using `X-Internal-Token`, `X-User-Id`, `X-User-Role`, `X-Request-Id`, and `Idempotency-Key`; never forward the browser JWT.
- Enforce recruiter approval, company membership/ownership, seeker ownership, admin-only quota controls and AI rate class.
- Assemble domain context through module facades; do not query another module's tables directly.
- Map ai-service errors to the existing API_CONTRACT error envelope.
- Add the job-publish AI request ownership/type/status validation hook.

### P1 — AI frontend
- Recruiter AI Job Studio: prompt inputs, generation, editable draft, quality issues, quota visibility, then create job with `aiRequestId`.
- Seeker recommendations and career insights pages.
- AI request history/detail and usage display.
- Admin quota policies/overrides and usage screens where the contract requires them.
- MSW handlers and unit/component/e2e test sources.

### P2 — Storage
- Implement MinIO/S3 adapter behind existing resume/avatar/logo storage ports.
- Keep local filesystem adapter as zero-cost default/fallback.
- Add configuration, compose profile wiring, upload/download authorization and adapter tests.
- Do not log storage credentials or expose object-store URLs/keys.

### P3 — API specification and generated types
- Add the documented ADR for springdoc.
- Generate and commit `docs/openapi/openapi.v1.json`; never hand-edit the generated snapshot.
- Wire `pnpm gen:api` and migrate hand-written API DTO types to generated types.
- Add CI drift check for the snapshot.

### P4 — Hardening and verification
- Complete remaining backend authorization/rate-limit/cache/upload ITs.
- Add/finish Playwright seeker, recruiter and admin critical flows and accessibility checks.
- Add Playwright to the integration workflow; review Spotless/JaCoCo/OpenAPI drift checks.
- Validate backend, frontend and ai-service in order; validate compose profiles and a fresh-database smoke flow.
- Review cookie/CORS/proxy trust, Redis fail-open behavior, upload virus-scan hook, account deletion retention and secrets history.
- Update `docs/status/backend.md`, `frontend.md`, `ai-infra.md`, and `HANDOFF.md` after implementation and validation.

## Definition of done

A feature is complete only when its contract, backend, frontend, authorization, tests, events/notifications, documentation and configuration agree. A static source addition is not a verified feature. The final handoff must clearly separate implemented, statically inspected, and actually tested behavior.
