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


## Progress update — 2026-11-22
- Added backend-only AI gateway routes for recruiter job drafting and job validation, user-owned request history/detail, usage, and admin quota-policy/usage reads.
- Gateway uses the separate `AI_INTERNAL_TOKEN` and forwards only verified user ID/role/request ID; it never forwards the browser bearer token.
- Recruiter job drafting checks recruiter approval and binds the request to the recruiter's own company membership.
- AI routes are protected by the existing security contributor mechanism and classified under the dedicated 10/min AI rate-limit class.
- Added backend AI service URL/token configuration and app-profile service wiring.
- Frontend AI Job Studio and typed client added; generation preview includes quality issues, quota feedback, and copy-to-clipboard. AI drafts are not auto-published.
- Frontend shell refreshed: responsive role-aware navigation, visual design tokens, landing page, metadata, and footer.
- Removed personal-looking seed credentials from `.env.example`; demo seeding now defaults off.
- **Not verified:** Maven, pnpm typecheck/lint/tests, Docker Compose, or Playwright have not been run in this environment. AI draft-to-job persistence is still blocked by the existing `JobService.create` guard rejecting `aiRequestId`; recommendations/career insight context assembly and S3-compatible storage remain open.


## Follow-up — 2026-11-22
- Added admin quota policy updates, per-user quota overrides and override deletion to the backend gateway; all routes require ADMIN both at the backend method boundary and in ai-service.
- Corrected AI gateway response handling to unwrap internal envelopes into the backend's standard envelope and preserve cursor metadata for request history.
- Added a unit test for the AI-specific rate-limit class.
- Opened draft PR #10 against `develop` for CI/review: https://github.com/gamechangerSumit/jobforge/pull/10. GitHub reported `mergeable=false` at creation time; investigate before any merge. No workflow runs were visible when checked.


## Admin AI UI follow-up — 2026-11-22
- Added `/admin/ai` quota and usage dashboard with daily/monthly role budgets, rate limits, active toggles, save actions, and 30-day aggregate usage table.
- Added typed admin quota policy/usage client and admin navigation entry.
- Still static/unverified; include in frontend typecheck/build and test coverage before merge.
