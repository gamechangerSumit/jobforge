# Backend status (Dev 1)

## Phase 0 — Foundation (done, commit fa27ba5)
Kernel: envelope, error catalog, RequestId, Flyway-per-schema, health, ArchUnit.

## Phase 1a — Auth / users / profiles foundation (branch `feature/backend`) — NOT yet compiled or run by the author
**Implemented**
- Migrations (core): `V202610021000__core_identity` (users, refresh_tokens, verification_tokens, `set_updated_at`), `V202610021010__core_audit_logs` (audit_logs, `trg_audit_immutable`, `REVOKE UPDATE, DELETE … FROM jobforge_backend`), `V202610021020__core_profiles` (seeker_profiles, skills, seeker_skills, recruiter_profiles).
- Security: Spring Security resource server (HS256, nimbus), deny-by-default URL rules, `@PreAuthorize`, 401/403 envelopes, CORS, server-side `token_version` check, BCrypt(12), `SecurityRulesContributor` hook.
- Endpoints: `POST /auth/{register,login,refresh,logout,verify-email,resend-verification,forgot-password,reset-password,change-password}`, `GET /auth/me`, `GET|PATCH /users/me`, `GET|PUT /seekers/me/profile` (If-Match/ETag), `GET|PUT /recruiters/me`.
- Refresh cookie `jf_refresh` (HttpOnly, SameSite=Strict, Path=/api/v1/auth, 14 d), rotation with family reuse detection, `X-Requested-With: JobForge` required.
- Audit: `AuditService` (caller's tx, redaction) — USER_REGISTERED, USER_LOGIN_SUCCESS/FAILED, USER_LOGOUT, TOKEN_REUSE_DETECTED, PASSWORD_CHANGED, PASSWORD_RESET, EMAIL_VERIFIED.
- Persistence via Spring JDBC (`JdbcClient`) — no JPA introduced.

**Tests added**
Unit: PasswordPolicyTest, AuditRedactionTest, JwtPrincipalConverterTest (+ existing). Integration (Docker): AuthFlowIT, AuthorizationIT, ProfileApiIT, AuditLogImmutabilityIT; updated HealthEndpointIT, EnvelopeWebTest, TestDatabase.

**Remaining Phase 1**
Seeker skills/education/experience/resumes endpoints, `GET /users/{id}/public`, `GET /users/search`, avatar, account deletion (anonymize), companies (RC-2…), admin user management (AD-1), rate limiting + lockout (needs platform), outbox events (`USER_REGISTERED` etc. → needs platform), OpenAPI snapshot (springdoc), completeness score.

**Contract questions:** see `docs/requests/REQ-20261002-phase1-contract-gaps.md` (A1–A13).
**Verification to run:** `cd backend && mvn -B verify`; then start infra (`bash infrastructure/scripts/dev-up.sh`), export env (or `.env`), `mvn spring-boot:run`, check `/actuator/health/readiness`.


## Revision 3 (unverified, nothing built or run)
- Done: PATCH blank-clears optional text (SeekerAssetsService.orClear); platform/ratelimit (RateLimiter, RedisRateLimiter, UploadRateLimitInterceptor, UPLOAD class) + unit tests; account-deletion owner policy (CompanyAccessFacade.accountDeletionBlocker/releaseMembership, AuthService).
- Test profile disables the rate limiter (no Redis in ITs).
- ITs added: AvatarIT, CompanyLogoIT, AccountDeletionIT, AdminAuditAndCreateIT. Not done: owner-block ITs, job If-Match, completeness, upload 429 IT; search saved/applied flags; Redis caching of facets/skills; MinIO adapter; virus-scan hook.
- Contract: see docs/requests/REQ-20261009-contract-additions.md and CHANGELOG_CONTRACTS.md; contract-change PR still to open.

- Search flags: GET /jobs and /jobs/{id}/similar now set saved/applied for signed-in JOB_SEEKER callers (SearchProvider.flags, JobSearchService, JobSearchController; withdrawn applications do not count). Unit test JobSearchServiceFlagsTest; no IT yet (needs job fixtures).
- Cache: shared.cache.CacheService port + platform.cache.RedisCacheService (JSON, TTL mandatory, fails open) / NoopCacheService (CACHE_ENABLED=false, used in tests). Used for GET /jobs/facets (60 s, key = sha256 of filter) and GET /skills (120 s). Unit test RedisCacheServiceTest.

## Revision 4 (unverified, nothing built or run)
- Done: GET /admin/recruiters/{userId}; PATCH clearEndDate for education/experience; ITs SeekerHistoryPatchIT (clearEndDate, completeness, IDOR), AdminRecruiterDetailIT, JobLifecycleIT (If-Match on publish, 404/401 negatives); micrometer-registry-prometheus + optional management port.
- Infra (Dev 3 lane files edited by this session): .github/workflows (backend-ci, ai-ci, frontend-ci, infra-ci, security), compose profiles tools/monitoring/storage/ollama, backend healthcheck, infrastructure/monitoring/*.
- Verified by reading: UploadRateLimitInterceptor paths match the real routes (ApiPathConfig prefixes /api/v1).

## Revision 8 (unverified, nothing built or run)
- Done: rate classes AUTH (10/min), SEARCH (60/min), DEFAULT (120/min), WRITE_COMMUNITY (30/min) and AI (10/min) now have contract limits in `RateLimitProperties.limitFor`; `RequestRateLimitInterceptor` applies AUTH/SEARCH/DEFAULT to `/api/v1/**` (uploads keep their own interceptor; `/auth/me` and `/auth/refresh` count as DEFAULT). Tests: `RequestRateLimitClassifierTest`, `RedisRateLimiterTest.contractLimitsApplyToEveryClass` (replaces the old "unwired classes are not throttled" test).
- Risk: the limiter subject for anonymous callers is the client IP. Behind the Next.js rewrite every browser may look like one IP unless `ClientInfo` reads a trusted `X-Forwarded-For`; verify before enabling in shared environments, or set `RATE_LIMIT_ENABLED=false` for local demos.

## Session 0 stabilization (unverified, nothing built or run)
- No production code changed. Static audit: job search/public security rules, recruiter onboarding chain, job lifecycle, application flow and migration order (resumes before applications) match the contracts. No `AuthService`/`CompanyAccessFacade` bean cycle found by reading.
- 403 on `GET /jobs`: no URL-rule cause found in source (ERROR dispatch is already permitted). Remaining sources: CORS rejection for an unlisted `Origin`, or a suspended account's token (403 `ACCOUNT_SUSPENDED`).
- Tests added: `search/PublicJobEndpointsIT` (public discovery anonymous 200, query validation 400, unmapped 404, protected /jobs-prefixed routes, draft invisibility). `AuthorizationIT` admin assertion updated (`/admin/users` now 200). Not executed.
- Open: ArchUnit sweep, notes/rating authorization audit, rate-limit client IP behind Next.js rewrite.

### Session 0 (continued) — additional static changes (nothing built or run)
- Layering: `NotificationResponse` moved `notification.api` → `notification.app` (app layer no longer imports api). `ArchitectureTest.appDoesNotDependOnApi` now exempts `shared.api` (kernel paging/envelope types), which the old `..api..` pattern wrongly matched for ~15 services.
- Static scan: no unresolved `com.jobforge` imports, no duplicate FQNs, no cross-module imports outside `facade`/`events`, no api→infra or domain→outer-layer imports. (Known: `NotificationService` still uses `JdbcTemplate` inside app; no ArchUnit rule forbids it.)
- Config: `server.forward-headers-strategy: native` (Tomcat RemoteIpValve trusts X-Forwarded-For only from internal/private peers) so rate limits and audit see real client IPs behind the Next.js rewrite; `ClientInfo` doc updated. Dev profile CORS defaults include `http://127.0.0.1:3000`; `.env.example` updated.
- Audit (no change needed): application access (seeker-owned/recruiter-company 404s, approved-recruiter gate), note author-only edit/delete, rating 1–5, transition matrix equals DATABASE_SCHEMA §3.6.
- Tests added (not executed): `application/ApplicationFlowIT` (Idempotency-Key, duplicate, no resubmit after withdraw, If-Match, state machine, seeker/recruiter IDOR, rating bounds).

## Session 1 - Interviews (not compiled/run)
Module `interview` complete per API_CONTRACT §12.7; ApplicationFacade extended and implemented by `ApplicationFacadeService`; events `jobforge.interviews.v1`; notification consumer updated; tests: InterviewAuthorizationIT, InterviewLifecycleIT, InterviewStateMachineTest, InterviewSchedulingTest. See HANDOFF revision 10 for open items.

## Session 2A - Interviews backend (static audit + hardening; nothing built, run or tested)
**Starting point:** the uploaded repository already contained the complete interview backend recorded above as "Session 1 - Interviews". Session 2A did not rebuild it. It audited the module by reading against DATABASE_SCHEMA §3.5/§4.5/§13, API_CONTRACT §12.7 and ARCHITECTURE §9/§14, then closed the gaps below.

**Audit result (module `interview`, layers api -> app -> domain -> infra, plus events)**
- Endpoints per API_CONTRACT §12.7: `POST /applications/{id}/interviews`, `GET /interviews`, `GET /interviews/{id}`, `PATCH /interviews/{id}`, `POST /interviews/{id}/cancel`, `POST /interviews/{id}/respond`, `POST /interviews/{id}/complete`. All in `InterviewController`, thin, `@PreAuthorize` per role (no `permitAll`).
- Authorization: seekers only reach interviews of their own applications; recruiters only those of applications to jobs of their own company and only when approved; ADMIN is not listed in the contract and gets 403; every out-of-scope or unknown id answers 404 (IDOR-safe). `applicationId` list filter can only narrow the caller's scope.
- Cross-module access only through `ApplicationFacade` (implemented by `ApplicationFacadeService`), `JobFacade`, `CompanyAccessFacade`, `ProfileFacade`, `UserFacade`. No other module's repository is used.
- Status moves of the application (SHORTLISTED -> INTERVIEW) go through `ApplicationService.changeStatus` (history, audit, `ApplicationStatusChanged`).
- Audit: `INTERVIEW_SCHEDULED`, `INTERVIEW_CANCELLED` (the only interview actions in DATABASE_SCHEMA §13; also written without actor when an application closure cancels interviews). Events through `EventPublisher` (outbox) on `jobforge.interviews.v1`: InterviewScheduled/Updated/Cancelled/Responded, ids and schedule metadata only.
- State rules: `InterviewStateMachine` + `InterviewScheduling` (409 `INVALID_STATE_TRANSITION`, 422 for time rules, 409 `STALE_VERSION` on concurrent change).

**Changed in 2A**
- `interview/infra/JdbcInterviewRepository.update`: the conditional UPDATE now also requires `updated_at` to equal the value that was read (the table has no `version` column). Previously only the status was guarded, so two recruiters editing different fields of a `SCHEDULED` interview could silently overwrite each other. A lost race now yields 409 `STALE_VERSION`. `InterviewRepository` Javadoc updated. All callers derive the new state from a freshly read row (checked by reading).
- New `test/.../interview/app/InterviewServiceAccessTest` (pure Mockito, no Docker): seeker ownership, recruiter company scope, foreign recruiter on read/update/cancel/complete/schedule, unknown ids, unapproved recruiter (403 `RECRUITER_NOT_APPROVED`), ADMIN refusal, list scoping incl. foreign `applicationId`, final-status and DECLINED invalid transitions, idempotent repeat answer, audit + event on schedule/cancel and no free text in event payloads.
- Existing tests kept: `InterviewAuthorizationIT`, `InterviewLifecycleIT` (Docker), `InterviewStateMachineTest`, `InterviewSchedulingTest`, `InterviewApplicationEventsConsumerTest`.
- Static check done in this session: the new test and repository change were run through `javac` against the project classes in `backend/target/classes`; no errors other than the unavailable third-party jars (Mockito, AssertJ, JUnit, Spring), and no project-symbol or constructor mismatches. This is not a build and not a test run.

**Not completed / open**
- Nothing was compiled by Maven, started or tested in this session. Run first: `cd backend && mvn -B -Dtest='Interview*Test' test`, then `mvn -B verify` (ITs need Docker).
- No audit actions exist in DATABASE_SCHEMA §13 for reschedule, respond or complete, so none are written (not invented). Add them via a contract change if wanted.
- Overlap check (`existsActiveOverlap`) is read-then-insert; two simultaneous schedule calls for one application can both pass. A DB exclusion constraint needs a schema change (not allowed in this session).
- ADMIN access: ARCHITECTURE §9 lists admin "read", API_CONTRACT §12.7 lists none; the contract wins (403). Confirm with the owners.
- `applicationIdsOfCompany/Seeker` feed an `IN (...)` list; fine for the current scale, replace with a join through a facade-provided query if lists grow large.
- Interview event payloads and notification mapping are defined by this module (not by ARCHITECTURE); the notification owner (Dev 3 lane) must confirm. `NotificationKafkaConsumer` was already edited in the uploaded zip.

**Carry-forward to Session 2B (frontend)**
- Response shape and rules are in API_CONTRACT §12.7 ("Interview object"); frontend interview files already exist in the uploaded zip and were not touched or reviewed in 2A.
- Hygiene: the uploaded zip contained `.env`, `.git`, `backend/target`, `frontend/.next`, `backend/.idea`; none are included in the 2A archive. Rotate anything in `.env` if it was ever shared.

### Session 2A follow-up (still not built, run or tested)
- Overlap race closed without a schema change: `JdbcInterviewRepository.existsActiveOverlap` now first takes a transaction-scoped Postgres advisory lock per application (`pg_advisory_xact_lock`), so concurrent schedule/reschedule calls for one application run their "check, then write" one after the other (callers are `@Transactional`). This replaces the earlier open item "non-atomic overlap check". A DB exclusion constraint would still be the stronger guarantee but needs a DATABASE_SCHEMA change.
- Still open (needs a decision, not code): audit actions for reschedule/respond/complete are not in DATABASE_SCHEMA §13; ADMIN read access (ARCHITECTURE §9 says read, API_CONTRACT §12.7 lists none; contract followed).
