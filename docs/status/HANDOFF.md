# JobForge handoff - revision 10 (nothing below was compiled, built, tested or run)

## Session 1 - Interview module (revision 10, coding only)
Static work only: nothing was compiled, built, tested, run or started. Expect compile/type/test errors on the first build.
- Backend `interview` module (api/app/domain/infra/events): schedule, list, get, patch, cancel, respond, complete. Table `core.interviews` was already migrated; **no new migration**.
- `ApplicationFacade` extended (recruiterContext, seekerContext, contexts, prepareForInterview, applicationIdsOfCompany/Seeker). Its implementation moved to the new `ApplicationFacadeService` (avoids a bean cycle application->job->application); `ApplicationLookupService` now only implements `JobApplicationLookup`. Scheduling moves SHORTLISTED->INTERVIEW through `ApplicationService.changeStatus` (history, audit, event).
- Events on `jobforge.interviews.v1`; `NotificationKafkaConsumer` interview handlers rewritten (reschedule/respond notify every time, recruiter is told about answers). It now also reads `seekerUserId`/`to` from application events (it previously read `seekerId`/`status`, which the producer never sends).
- Contract gaps resolved and recorded in API_CONTRACT §12.7 + CHANGELOG_CONTRACTS (response object, rules, admin 403). Decision to confirm: ARCHITECTURE §5 matrix says admin may *read* interviews; the contract lists only S and R, so admin gets 403.
- Frontend: `/recruiter/interviews`, `/recruiter/interviews/[id]`, `/interviews`, `/interviews/[id]`, interview panel (schedule/reschedule/cancel/complete) on the recruiter application page, nav links. Files: `lib/api/interviews.ts`, `lib/interviews/time.ts`, `lib/validation/interviews.ts`, `features/interviews/*`, `types/interviews.ts`.
- Test sources (not run): `InterviewAuthorizationIT`, `InterviewLifecycleIT`, `InterviewStateMachineTest`, `InterviewSchedulingTest`, frontend `interview-time`, `interview-schema`, `InterviewCard` tests.

### Interview follow-up (done after the first pass)
- Auto-cancel: `InterviewApplicationEventsConsumer` (group `jobforge-interview`, topic applications) cancels open interviews on `ApplicationWithdrawn` and `ApplicationStatusChanged -> REJECTED` via `InterviewService.cancelOpenInterviewsOfClosedApplication` (idempotent, system audit, `InterviewCancelled.cause`). Notification consumer: withdrawal notifies the recruiter, rejection adds nothing.
- `GET /interviews?applicationId=` (contract + changelog updated); the recruiter application panel uses it instead of client-side filtering.
- Playwright `tests/e2e/interviews.spec.ts` (mocked API, needs only the dev server). IT fixture assumptions re-checked statically against the migrations (resumes, applications, application_status_history, outbox_events, admin approve/verify paths).
- Tests added: `InterviewApplicationEventsConsumerTest`, lifecycle/authorization IT cases for the filter and system cancellation.

### Interview-only remaining work (needs a contract/product decision or a first build)
- Reschedule/respond/complete are not audited (no such actions in DATABASE_SCHEMA §13) and `complete` emits no event (not in the ARCHITECTURE catalog). Add them via a contract change if wanted.
- Notification deep links to interviews wait for the notification deepLink contract change.
- Reminders before the interview need a scheduler and a contract; not started.
- Company/seeker list queries use an application-id `IN` list; revisit with a denormalised company column if volume grows.
- Nothing here has been compiled or run: first build must confirm the ITs, the Kafka consumer wiring and the e2e selectors.

## Done in revision 9 (on top of v8)
- Job create form: root cause of "One or more fields are invalid" was a looser client schema plus hidden server details. `jobSchema` now mirrors the backend
  (country `^[A-Z]{2}$` upper-cased, currency `^[A-Z]{3}$`, blank salary no longer coerced to 0, whole-number salary, skills <= 20 x 50 chars),
  `buildJobPayload()` omits blank optionals, and the form maps `error.details[].field` (location.country, salary.currency, skills[..]) onto the inputs and lists them.
  Edit page still uses `jobSchema` but not yet `buildJobPayload` - align it.
- New migrations: `core/V202610070100__core_interviews_reports.sql` (interviews, reports, moderation_actions) and
  `community/V202610070200__community_core.sql` (posts, tags, post_tags, comments, likes, saves, shares, follows, mentions). No Java/UI for them yet.
- `docs/NEXT_CLAUDE_PROMPT.md`: ready-to-paste prompt for the remaining slices.

## Done in revision 8 (on top of v7)
- Accessible reason dialog (`useReasonDialog`) replaces `window.prompt` on all five admin pages, with tests.
- Rate classes AUTH/SEARCH/DEFAULT (+ WRITE_COMMUNITY/AI limits) wired through `RequestRateLimitInterceptor` with unit tests.
- Workflows `integration.yml` (compose smoke) and `release.yml` (GHCR + SBOM); hadolint for all three Dockerfiles; Grafana dashboard provisioning.
- Status files updated (backend, frontend, ai-infra).

## 0. Remaining feature slices (none of these has Java or UI yet)
Reports/moderation, community, analytics (needs job_daily_stats migration), AI platform (ai schema + gateway + quotas + job generation),
notification stream/email outbox/preferences/deep links, OpenAPI snapshot + generated types, MinIO adapter, Playwright specs. See docs/NEXT_CLAUDE_PROMPT.md.

## 1. Do first
1. `cd backend && mvn clean verify`; fix compile errors (AuthService <-> CompanyAccessFacade bean cycle, constructor changes from revisions 2-8, Prometheus dependency).
2. `ArchitectureTest` (new modules: admin, audit, storage; auth depends on company.facade).
3. `cd frontend && pnpm install && pnpm typecheck && pnpm lint && pnpm test && pnpm build` (Zod 4 + RHF resolver typing, React 19 ref forwarding on Input/Select, MSW + jsdom fetch shim, `<dialog>` in jsdom).
4. `cd ai-service && mvn package`; start with AI_INTERNAL_TOKEN.
5. Push a branch to validate the 7 workflows (pnpm version, hadolint, compose config with placeholder secrets).
6. `docker compose config` for every profile, `docker compose up -d`, then `--profile app up -d --build`; confirm backend healthcheck, MANAGEMENT_PORT behaviour.
7. Fresh DB + smoke flow: register recruiter, company, admin approves, publish, search, apply, status change, avatar, logo, admin invite, delete account (owner block).
8. Confirm the anonymous client-IP used by the rate limiter behind the Next.js rewrite (X-Forwarded-For trust) or set RATE_LIMIT_ENABLED=false for demos.

## 2. Backend not done
- ITs: delete-account owner blocks, upload 429 + Retry-After (needs Redis Testcontainer), search saved/applied flags, facet/skill cache behaviour, rate limiting end to end (AUTH 429).
- IT assumptions to verify on first run: wrong-password delete returns 4xx; unknown avatar/logo id returns 404; company public GET only after VERIFIED; table names core.company_members / core.jobs; job response exposes data.version.
- No virus-scan hook on uploads; login-specific 5/min per IP+email limit (contract section 9) is not separate from AUTH 10/min per IP.
- Account deletion keeps applications/notes; orphaned companies are not auto-suspended (policy review).
- Avatar/logo storage is local disk only; MinIO adapter not written (storage compose profile unused).
- Notifications have no deepLink (needs contract change).
- Reports, community, interviews, analytics, AI admin endpoints (later phases).
- AI phase 3: backend gateway, quota (Redis Lua + PG ledger), Idempotency-Key, JOB_GENERATION validation (publish still rejects aiRequestId with 422), ai-service pipeline and Ollama provider.
- OpenAPI snapshot `docs/openapi/openapi.v1.json` does not exist; springdoc generation and CI diff not set up (needs ADR for springdoc).

## 3. Frontend not done
- `pnpm gen:api` once the OpenAPI snapshot exists; replace hand-written types (seeker.ts, users.ts, account.ts, companies.ts, admin.ts, notifications.ts).
- Component tests: ProfileForm, HistorySections (clearEndDate), CompanyEditForm, LogoUpload, AvatarUpload, MentionInput.
- Playwright critical flows (recruiter onboarding to publish, seeker apply to withdraw, recruiter pipeline) + axe pass on all new pages.
- Public company page is by id; add a by-slug read only if slugs are wanted in URLs.
- Wire MentionInput into community forms when that phase starts.

## 4. Infrastructure and process
- Playwright job inside integration.yml; Spotless/JaCoCo/OpenAPI-diff in backend-ci.
- Production hardening: COOKIE_SECURE true, restrictive CORS, secrets from environment only.
- Open the contract-change PR (REQ-20261008/09/10; rows already in API_CONTRACT 12.12 and CHANGELOG_CONTRACTS.md) and the ADR for micrometer-registry-prometheus.
- Rotate anything from the original archive (.env, cookie files) if it was ever committed or shared.

## 5. Known risks
- Profile completeness weights (headline 15, summary 15, location 10, title 10, 3+ skills 20, education 10, experience 10, primary resume 10) not re-verified against ProfileService.
- QueryParams rejects unknown parameters and Jackson fails on unknown properties: frontend bodies must match DTOs exactly.
- Profile PUT replaces: omitted optional fields are cleared.
- RedisRateLimiter fails open on Redis errors; consider failing closed for AUTH.
- With MANAGEMENT_PORT set, actuator leaves 8080; HealthEndpointIT and scripts calling 8080 will not match.
