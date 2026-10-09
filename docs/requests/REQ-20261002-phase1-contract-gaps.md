# REQ-20261002 — Phase 1 (auth/users/profiles) contract gaps and cross-lane needs

**From:** Dev 1 (backend) **To:** Dev 2 (API consumers), Dev 3 (platform/infra), contract owners
**Status:** proposal. No contract text was changed. Each item states the interim behaviour implemented in code.

## A. Contract questions (Dev 1 ↔ Dev 2; docs-only contract PR if accepted)

| # | Topic | Gap | Interim behaviour | Proposal |
|---|---|---|---|---|
| A1 | Task path | The Phase 1 task mentions `/api/v1/profiles/**`; API_CONTRACT has no such prefix (it defines `/seekers/me/profile`, `/recruiters/me`, `/users/me`). | Contract paths implemented. | Confirm no `/profiles/**` is intended. |
| A2 | Register without `handle` | Handle is optional in §10 but how it is derived is unspecified. | `<firstName letters, ≤20>_<6 hex>`, retried on collision. | Document derivation, or make `handle` required. |
| A3 | Register response / Location | §12.1 says `201 {user}` + Location. No user resource URL exists yet. | `data.user` = login user shape; no Location header. | Define Location target (`/users/{id}/public` once built). |
| A4 | Invalid/expired/used verify or reset token | No error code defined. | `422 BUSINESS_RULE_VIOLATED`. | Add a dedicated code (e.g. `TOKEN_INVALID`, 400) to §5.1. |
| A5 | Wrong `currentPassword` on change-password | Only `AUTH_INVALID_CREDENTIALS` (401) fits, which clients may treat as "session dead". | `401 AUTH_INVALID_CREDENTIALS`. | Consider `400 VALIDATION_FAILED` on field `currentPassword`. |
| A6 | `POST /auth/refresh` response shape | Only "new access token" is stated. | Same body as login (`accessToken, tokenType, expiresIn, user`) — a superset. | Document it. |
| A7 | Password-reset link TTL | ARCHITECTURE §8 gives 24 h for verification only. | 60 minutes. | Document. |
| A8 | `PATCH /users/me` fields | "name, handle, avatar settings" — no DTO. | `firstName`, `lastName`, `handle` (absent/null = unchanged). Avatar arrives with the storage module. | Specify DTO. |
| A9 | Seeker `PUT` | Editable set and PUT-vs-PATCH semantics not listed; `skills[]` item shape only implied by `PUT /seekers/me/skills`. | PUT = replace of: headline, summary, phone, location, currentTitle, yearsExperience, expectedSalary, noticePeriodDays, openToWork (default true), visibility (default RECRUITERS_ONLY), links. `skills[]` = `{skill, proficiency, years}` read-only. | Document. |
| A10 | `completenessScore` | Formula not specified; stored value stays 0. | Not computed. | Specify rules (also needed by AI features). |
| A11 | 405/406, unknown field | See REQ-20261001-error-catalog-gaps.md items 1–2 (unchanged). | — | — |
| A12 | Login of unverified users | §12.1 implies login works with `emailVerified=false`; EMAIL_NOT_VERIFIED guards actions. | Login allowed; guard applied per feature. | Confirm. |
| A13 | `companyVerified` in `/auth/me` | Companies tables not yet migrated. | Always `false` until the company slice. | — |

## B. Needed from Dev 3 (platform / infra)

1. **Rate limiting** (class AUTH 10/min per IP, ARCHITECTURE §15) and **login lockout** (Redis `auth:fail`) need the `platform` `RateLimiter`/`CacheService`. Not implemented in Phase 1; auth endpoints are currently unthrottled. Please prioritise; Dev 1 will wire them as soon as the interfaces exist.
2. **Email delivery:** ARCHITECTURE routes email through events → `email_outbox` → dispatcher (platform). Interim: `SmtpAuthMailer` sends directly via Spring Mail to Mailpit, after commit, failures swallowed (no tokens in logs). Replace with the outbox when it exists.
3. **Token-version cache:** `DbAccessTokenVerifier` does one DB read per authenticated request; swap to Redis (TTL 60 s) via `CacheService` later.
4. **`.env.example`** (shared file, not available to me): please confirm/add `JWT_SECRET`, `JWT_ACCESS_TTL_MINUTES`, `COOKIE_SECURE`, `COOKIE_DOMAIN`, `CORS_ALLOWED_ORIGINS`, `APP_BASE_URL`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`. `JWT_SECRET` must be ≥ 32 bytes (the app refuses to start otherwise).
5. **`SecurityRulesContributor`** (shared.security) is available for ai/notification/platform to register URL rules; the default is deny (authenticated).

## C. Needed from Dev 2

Email links point to `${APP_BASE_URL}/verify-email?token=…` and `${APP_BASE_URL}/reset-password?token=…`; please provide these routes (or tell me the real ones). The refresh call must send `X-Requested-With: JobForge` and credentials (cookie).
