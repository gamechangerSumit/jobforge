# JobForge completion tracker

_Last updated: 2026-11-25. Working branch: `feature/jobforge-completion-v2`. Draft PR: #10._

## Implemented

### Product and frontend
- Responsive landing page, design tokens, role-aware navigation, metadata and footer.
- Seeker experience: jobs, saved jobs, applications, interviews, profile, community and AI career insights.
- Recruiter experience: jobs, applications, interviews, analytics, AI Job Studio and AI candidate matching.
- Admin experience: approvals, users, moderation, reports, analytics, audit and AI quota controls.
- AI career insights shows profile strength, job recommendations, skill gaps, sampled job-market skills and next steps.
- AI candidate matching ranks active applicants for a selected published company job and links back to the original application.
- Playwright browser coverage includes public jobs and mocked community, interview and report/moderation flows. Axe WCAG 2.1 A/AA currently scans public job discovery.

### Backend and AI
- Backend AI gateway with internal service authentication, verified principal forwarding, recruiter approval/company checks, owner-scoped request history/detail, usage and admin quota controls.
- AI draft-to-job handoff validates request owner, operation and success state; recruiter reviews and saves the draft. AI copy is never auto-published.
- Seeker recommendations/career insights use server-built context from the authenticated seeker's professional profile and up to 10 public jobs.
- Recruiter candidate matching requires an approved recruiter, verified company membership, a published job owned by that company, active applicants and non-private profiles.
- Candidate identifiers stay in the backend: only ephemeral candidate references go to the AI provider, then results are linked to application IDs after the provider returns.
- Contact-like text in profile summaries/headlines is redacted before provider calls. Phone, email, expected salary, resume bytes/storage keys, application notes and account secrets are excluded.
- The AI service removes the entire trusted `context` object from persisted request input. Local-template provider tests cover seeker recommendations, candidate ranking and career insights.
- Local-template match scores are deterministic skill-overlap signals, not probability-of-hire estimates or automated hiring decisions.

### Storage and API contract
- Object storage abstraction has a local filesystem adapter (default) and an AWS S3-compatible adapter.
- Local S3 development/integration service uses PGSTY Silo, a maintained MinIO-compatible fork. The Testcontainers integration test verifies bucket creation and object store/load/delete against the pinned Silo release.
- Springdoc OpenAPI generation is opt-in, Swagger UI is disabled, and ADR-014 plus an export script are checked in.
- Integration CI exports the runtime OpenAPI JSON and generated TypeScript as an `openapi-contract` artifact. The checked-in snapshot and drift gate are not complete yet.
- Demo seeding defaults off and personal-looking seed credentials were removed from `.env.example`.

## Validation

Latest completed checks observed on the PR branch:
- Backend Maven `clean verify`: **passed**, including architecture tests and Silo-backed S3 integration test.
- AI service Maven `verify`: **passed**.
- Frontend lint, typecheck, tests and production build: **passed**.
- Infrastructure Compose validation, ShellCheck, Hadolint and Docker image build: **passed**.
- Gitleaks and Trivy filesystem scans: **passed** on the preceding security run.
- The most recent full-stack integration run was cancelled when the branch was updated; rerun it on the current head before considering the PR validated end-to-end.

## Remaining before production-ready

1. **Integration CI:** obtain a successful full-stack Compose smoke and Playwright/axe run on the current PR head.
2. **OpenAPI baseline:** retrieve and review the `openapi-contract` artifact, commit `docs/openapi/openapi.v1.json` and `frontend/src/types/generated-api.ts`, then add a drift check. Do not hand-author generated output.
3. **AI verification:** add integration tests for seeker context minimization and recruiter authorization/visibility boundaries; review consent, freshness, provider logging and retention.
4. **Accessibility:** extend axe checks from public job discovery to the main seeker, recruiter and admin journeys.
5. **Production security/release:** verify secure cookies, restrictive CORS, proxy trust, Redis failure policy, retention/account deletion, secret rotation, upload scanning, rollback and monitoring.
6. **Release process:** keep PR #10 draft until the latest checks pass and review is complete; then convert from draft and merge through the repository's required checks.

## Local commands

```bash
cd backend && mvn -B -ntp clean verify
cd ../ai-service && mvn -B -ntp verify
cd ../frontend && pnpm install --no-frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm test:e2e
cd .. && docker compose config
docker compose --profile app up -d --build --wait
bash scripts/export-openapi.sh
```

For local Silo, set `STORAGE_PROVIDER=s3`, use strong S3 credentials, start `docker compose --profile storage up -d minio` first, then start the app profile. For the containerized backend, set `S3_ENDPOINT=http://minio:9000`. Local disk remains the default. For production, use AWS S3 or another maintained provider with TLS, backups and lifecycle policies.

## Definition of done

A feature is complete only when contract, backend, frontend, authorization, tests, documentation and runtime configuration agree. Keep implementation status separate from verified CI status in every handoff.
