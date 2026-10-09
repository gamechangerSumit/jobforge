# Prompt for the next Claude session (paste this together with the latest JobForge ZIP and the 6 source-of-truth docs)

You are the primary senior full-stack engineer completing the attached JobForge project. The ZIP is the baseline. CODING ONLY: do not run the app,
Maven, pnpm, Docker or any server, and do not run tests or builds. The user runs everything afterwards. Read `docs/status/HANDOFF.md` first, then
DATABASE_SCHEMA.md > API_CONTRACT.md > ARCHITECTURE.md > MASTER_SPEC.md > CLAUDE.md (that precedence). Preserve all working Phase 0-2 behaviour
(advanced JobSearch with URL sync, auth, profiles, resumes, companies, jobs, applications, admin). Never weaken security. No paid services.

Work in vertical slices (DB -> domain -> repository -> service -> facade -> controller -> security -> DTO -> frontend API -> UI -> events/notifications -> tests),
following the existing module layout (api/facade/app/domain/infra/events), `Db`/`JdbcClient` style, `AuditService`, `EventPublisher` (outbox), `QueryParams`,
envelope + error codes from API_CONTRACT. Add `*AuthorizationIT`/unit test SOURCE for each slice, but do not run them. Use small shell checks only for
syntax (a javac parse-only pass over Java, YAML/JSON parsers) and an import-resolution script; both worked in earlier sessions.

Status of the baseline (all unverified by any build): migrations now exist for core, interviews, reports/moderation and community
(`V202610070100__core_interviews_reports.sql`, `community/V202610070200__community_core.sql`). No Java or UI exists yet for them.

Do these slices in order, one at a time, finishing each completely before the next:
1. INTERVIEWS (core.interviews exists): API_CONTRACT interview endpoints, scheduling/reschedule/cancel/seeker response, statuses, events + notifications,
   recruiter and seeker UI, tests.
2. REPORTS + MODERATION (core.reports, core.moderation_actions exist): create report, admin queue, actions (DISMISS/HIDE/REMOVE/WARN/SUSPEND), audit, notifications, admin UI.
3. COMMUNITY (community schema exists): posts, feed, likes, comments, saves, shares, follows, mentions/tags, counters in the same transaction, events,
   notifications, Next.js feed/create/edit/delete/detail/comments UI, MentionInput wiring, tests.
4. ANALYTICS: add `core.job_daily_stats` (+ job views) migration per DATABASE_SCHEMA, aggregation job, recruiter/job/funnel/platform/admin analytics APIs, dashboards.
5. AI PLATFORM: `ai` schema migrations (DATABASE_SCHEMA 7), backend `ai` gateway module (requests ownership/status/type, quota reserve/commit/release, usage ledger,
   Idempotency-Key, rate class AI), JOB_GENERATION + validation + recommendations + career insights, integrate `ai-service` over X-Internal-Token, publish validation
   of aiRequestId (currently 422), frontend AI Job Studio, tests. LocalTemplateProvider stays the default.
6. NOTIFICATIONS: SSE stream if the contract requires it, email outbox + preferences + deep links, idempotent consumers for jobs/users/applications/interviews/community/moderation/ai topics.
7. OPENAPI + STORAGE: springdoc + `docs/openapi/openapi.v1.json` snapshot (needs an ADR for the dependency), `pnpm gen:api`, replace hand-written types; MinIO/S3 adapter behind the
   existing ResumeStorage/avatar/logo ports with local fallback.
8. TESTS: remaining ITs listed in HANDOFF, Playwright specs (seeker, recruiter, admin) and axe configuration - write only, do not run.

At the end: static consistency audit (parse + imports + YAML), update `docs/status/*.md` and `docs/status/HANDOFF.md`, remove node_modules/.next/target/.env/cookies/logs,
and package ONE zip plus a docx of whatever remains. If you reach about 90% of your budget, stop starting new slices: package the zip and the unfinished-work docx
(same structure as docs/status/HANDOFF.md) and hand over. Never claim a build or test ran if it did not.
