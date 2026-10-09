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
