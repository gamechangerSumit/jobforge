# JobForge completion tracker

_Last updated: 2026-11-25. Working branch: `feature/jobforge-completion-v2`. Draft PR: #10._

## Implemented on this branch

### Product and frontend
- Responsive landing page, design tokens, role-aware navigation, metadata and footer.
- Seeker experience: jobs, saved jobs, applications, interviews, profile, community and AI career insights.
- Recruiter experience: jobs, applications, interviews, analytics, AI Job Studio and AI candidate matching.
- Admin experience: approvals, users, moderation, reports, analytics, audit and AI quota controls.
- AI career insights page shows profile strength, recommendations, skill gaps, a sampled job-market skill snapshot and next steps.
- Recruiter AI candidate page ranks active applicants for a selected published company job and links back to the original application.
- Playwright browser coverage includes public jobs and mocked community, interview and report/moderation flows. An axe WCAG 2.1 A/AA scan currently covers public job discovery.

### Backend and AI
- Backend AI gateway with internal service authentication, verified principal forwarding, recruiter approval/company checks, owner-scoped request history/detail, usage and admin quota controls.
- AI draft-to-job handoff validates request owner, operation and success state; recruiter reviews and saves the draft. AI copy is never auto-published.
- Seeker recommendations/career insights use server-built context from the authenticated seeker's own professional profile and up to 10 public jobs.
- Recruiter candidate recommendations require an approved recruiter, a published job owned by their company, active applicants to that job, and profiles whose visibility is not PRIVATE.
- Trusted AI context excludes phone, email, expected salary, resume bytes/storage keys, application notes and account secrets. The AI service strips secret-like keys and removes the entire trusted `context` object from persisted request input.
- Local-template provider computes deterministic skill-overlap signals, profile completeness suggestions and sampled skill demand. Match scores are decision support, not hiring predictions.
- Dedicated AI rate-limit classification and private AI-service Compose wiring.

### Storage and API contract
- Object storage abstraction has a local filesystem adapter (default) and an AWS S3-compatible adapter. MinIO is supported through endpoint override; object URLs are not exposed publicly by the adapter.
- Springdoc OpenAPI dependency and metadata, opt-in `/v3/api-docs` endpoint, disabled Swagger UI, export script, and ADR-014 are in place.
- Integration CI exports the runtime OpenAPI JSON and generated TypeScript types as an `openapi-contract` artifact. The checked-in snapshot and drift gate are not complete yet.
- Demo seeding defaults off and personal-looking seed credentials were removed from `.env.example`.

## Validation

A prior baseline commit `728ee6467db4e7c1725ae40f9f1e1b0cf356cb71` passed backend Maven, AI-service Maven, frontend lint/typecheck/tests/build, Gitleaks/Trivy, infrastructure image/compose checks and full-stack Compose smoke tests.

New storage, OpenAPI, personalized AI, recruiter candidate matching and axe changes are being validated on the latest PR commit. The last observed CI attempt caught:
- A missing S3 test import (fixed).
- A frontend JSX apostrophe lint error (fixed).
- Missing Springdoc dependency declaration (fixed).

Do not describe the newest code as verified until all workflows for the latest commit finish successfully.

## Remaining before production-ready

1. **Latest CI:** ensure backend, AI, frontend, security, infra and integration workflows pass on the current PR head. Fix every new failure.
2. **OpenAPI baseline:** retrieve and review the `openapi-contract` artifact, commit `docs/openapi/openapi.v1.json` and `frontend/src/types/generated-api.ts`, then add a drift check. Do not hand-author generated output.
3. **Storage integration:** add MinIO-backed integration tests for bucket provisioning, upload/download/delete lifecycle and authorization. Add an upload malware-scanning hook before production.
4. **AI verification:** add integration tests for seeker context minimization and recruiter authorization/visibility rules; review consent, freshness, provider logging and retention.
5. **Accessibility:** extend axe checks from public job discovery to the main seeker, recruiter and admin flows.
6. **Production security/release:** verify secure cookies, restrictive CORS, proxy trust, Redis failure policy, retention/account deletion, secret rotation, deployment rollback and production monitoring.
7. **Release process:** keep PR #10 draft until the latest checks pass and review is complete; then convert from draft and merge through the repository's required checks.

## Local commands

```bash
cd backend && mvn -B -ntp clean verify
cd ../ai-service && mvn -B -ntp verify
cd ../frontend && pnpm install --no-frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm test:e2e
cd .. && docker compose config
docker compose --profile app up -d --build --wait
bash scripts/export-openapi.sh
```

For MinIO, set `STORAGE_PROVIDER=s3`, use strong S3 credentials, start `docker compose --profile storage up -d minio` first, then start the app profile. For the containerized backend, set `S3_ENDPOINT=http://minio:9000`. Local disk remains the default.

## Definition of done

A feature is complete only when contract, backend, frontend, authorization, tests, documentation and runtime configuration agree. Keep implementation status separate from verified CI status in every handoff.
