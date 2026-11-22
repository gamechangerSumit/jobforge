# JobForge completion tracker

_Last updated: 2026-11-22. Working branch: `feature/jobforge-completion-v2`. PR: #10._

## Implemented on this branch

- Consolidated the existing platform slices: jobs, applications, interviews, reports/moderation, community, analytics, notifications and the AI service.
- Backend AI gateway with internal service authentication, verified principal forwarding, recruiter approval/company checks, owner-scoped request history/detail, usage and admin quota controls.
- AI draft-to-job handoff validates request owner, operation and success state; the recruiter reviews and saves a draft. AI copy is never auto-published.
- Recruiter AI Job Studio, admin quota dashboard, responsive role-aware navigation, landing-page redesign, design tokens, metadata and footer.
- Dedicated AI rate-limit bucket and private service wiring in Compose.
- Object storage abstraction now has a local filesystem adapter and an S3-compatible adapter using AWS SDK v2. S3 supports MinIO through endpoint override and AWS S3 through regional endpoints. Bucket access remains server-side; the adapter does not publish object URLs.
- Playwright browser suite covers the jobs page and mocked community, interview and report/moderation flows. The integration workflow now installs Chromium and runs `pnpm test:e2e`.
- Demo seeding defaults off and personal-looking seed credentials were removed from `.env.example`.

## Verified baseline before the latest storage / Playwright commits

For commit `728ee6467db4e7c1725ae40f9f1e1b0cf356cb71`, GitHub Actions reported success for:
- Backend Maven clean verify.
- AI service Maven verify.
- Frontend install, lint, typecheck, tests and production build.
- Gitleaks and Trivy filesystem scans.
- Compose validation, ShellCheck, Hadolint and Docker image builds.
- Full-stack Compose smoke tests: public job search/facets, protected-route authorization and frontend jobs page.

The S3 adapter and Playwright workflow changes were committed after that verified baseline. They still require a fresh CI run; do not treat them as validated yet.

## Remaining before production-ready

1. **Personalized AI context:** assemble seeker profile, skills, preferences, application history and job/candidate context through module facades; add consent, minimization, freshness and authorization tests before exposing recommendations or career insights.
2. **OpenAPI:** add the springdoc ADR/dependency, generate and commit `docs/openapi/openapi.v1.json`, generate frontend API types, and enforce a CI drift check. Do not hand-author a snapshot and call it generated.
3. **Storage validation:** run backend Maven verification with the AWS SDK dependency; test S3 adapter against MinIO in integration CI; verify bucket provisioning, object lifecycle and upload/download authorization. The local adapter remains the default.
4. **Browser quality:** run the new Playwright workflow, fix selectors/flows, and add axe-based accessibility checks for the main seeker, recruiter and admin journeys.
5. **Production review:** verify secure cookies, restrictive CORS, proxy trust, Redis fail-open policy, upload malware-scanning hook, retention/account deletion policy, secret rotation and deployment rollback.
6. **Release process:** update status docs after CI passes, convert PR #10 from draft only after review, then merge using the repository's required checks.

## Local commands

```bash
cd backend && mvn -B -ntp clean verify
cd ../ai-service && mvn -B -ntp verify
cd ../frontend && pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm test:e2e
cd .. && docker compose config
docker compose --profile app up -d --build --wait
```

For MinIO, set `STORAGE_PROVIDER=s3`, use strong S3 credentials, start `docker compose --profile storage up -d minio` first, then start the app profile. For the containerized backend, set `S3_ENDPOINT=http://minio:9000`. The default provider is local disk.

## Definition of done

A feature is complete only when contract, backend, frontend, authorization, tests, events/notifications, docs and runtime configuration agree. Separate implemented code from verified behavior in every handoff. Keep PR #10 as draft until the post-storage CI run and the remaining production-readiness work are complete.
