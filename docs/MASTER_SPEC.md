# JobForge — Master Specification

> **Status:** Authoritative. Version 1.0.0
> **Companions:** `API_CONTRACT.md`, `DATABASE_SCHEMA.md`, `ARCHITECTURE.md`, `CLAUDE.md`
> **Rule of precedence if documents disagree:** `DATABASE_SCHEMA.md` (data) > `API_CONTRACT.md` (HTTP) > `ARCHITECTURE.md` (structure/flows) > `MASTER_SPEC.md` (intent). Any disagreement is a bug: open a contract-change PR (see §13).

Sections are numbered so humans and Claude sessions can cite them (e.g. "MASTER_SPEC §6 D-05").

---

## 1. Product Vision

JobForge is a production-oriented, AI-assisted job marketplace and professional community. It connects **job seekers** with **recruiters/companies**, gives recruiters an **AI Job Studio** that drafts and quality-checks job postings, gives seekers **AI career insights and recommendations**, and embeds a **community** (posts, referrals, hiring announcements, events) so hiring and networking happen in one place.

Success criteria for v1:
- A seeker can register, build a profile, search jobs, apply, and track the application to a terminal status.
- A recruiter can create a company, create/publish jobs (optionally AI-drafted), run a candidate pipeline, schedule interviews, and see analytics.
- An admin can moderate users, companies, jobs, and community content, audit actions, and monitor AI usage.
- Everything runs locally with **zero paid services**, and switching to the Claude API later requires **configuration and one provider class only**.

## 2. Roles

| Role | Created by | Summary |
|---|---|---|
| `JOB_SEEKER` | Self-registration | Profile, search, apply, track, community, AI insights |
| `RECRUITER` | Self-registration (requires admin approval before publishing) | Company, jobs, pipeline, interviews, analytics, AI Job Studio |
| `ADMIN` | Bootstrap seed / another admin only (never self-registration) | Moderation, management, audit, analytics, AI monitoring |

A user has exactly **one** role. Role changes are an admin operation and are audited.

## 3. Feature Catalog

Phase column refers to §9. IDs are stable and used in commit messages/issues.

| ID | Area | Feature | Phase |
|---|---|---|---|
| AUTH-1..6 | Auth | Register, login, refresh, logout, email verification, password reset/change | 1 |
| JS-1 | Seeker | Profile (headline, summary, links, preferences, visibility) | 1 |
| JS-2 | Seeker | Skills, education, experience CRUD | 1 |
| JS-3 | Seeker | Resume upload/list/primary/delete/download | 1 |
| JS-4 | Seeker | Job discovery + advanced search (keyword, location, filters, sort) | 2 |
| JS-5 | Seeker | Saved jobs | 2 |
| JS-6 | Seeker | Apply, withdraw, application tracking + status history | 2 |
| JS-7 | Seeker | Notifications (in-app + email) | 3 |
| JS-8 | Seeker | AI job recommendations, AI career insights | 5 |
| RC-1 | Recruiter | Recruiter profile, company profile, company members | 1 |
| RC-2 | Recruiter | Job create/edit/publish/unpublish/close | 2 |
| RC-3 | Recruiter | Applications list, candidate pipeline, candidate profile view, notes, rating | 2 |
| RC-4 | Recruiter | Interview scheduling/management | 3 |
| RC-5 | Recruiter | AI Job Studio (generate, validate) | 3 |
| RC-6 | Recruiter | Recruiter analytics | 5 |
| RC-7 | Recruiter | AI candidate ranking for a job | 5 (optional) |
| AD-1 | Admin | User / recruiter / company management | 1–2 |
| AD-2 | Admin | Job moderation, community moderation, reports queue | 2, 4 |
| AD-3 | Admin | Audit log viewer | 5 |
| AD-4 | Admin | Platform analytics | 5 |
| AD-5 | Admin | AI usage monitoring, quota policy management | 3, 5 |
| CM-1 | Community | Posts (7 types), edit/delete, tags, feed | 4 |
| CM-2 | Community | Like, comment, save, share | 4 |
| CM-3 | Community | Follow (users, tags, companies), mentions | 4 |
| CM-4 | Community | Report, moderation hooks, notifications | 4 |
| AI-1 | AI | Job generation, validation, recommendations, insights | 3, 5 |
| AI-2 | AI | Quota, rate limit, request tracking, audit | 3 |

Community post types (fixed enum): `DISCUSSION, HIRING, REFERRAL, PROJECT, CAREER_ADVICE, TECHNICAL, EVENT`.

## 4. Technology

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java 21, Spring Boot 3.x (latest stable 3.x), Spring Security, Spring Data JPA, Flyway, Maven, springdoc-openapi | Modular monolith |
| AI service | Java 21, Spring Boot 3.x, Maven | Separate deployable (D-01/D-02) |
| Database | PostgreSQL 16 (extensions: `citext`, `pg_trgm`, `unaccent`) | One instance, four schemas |
| Cache / counters | Redis 7 | Never the source of truth |
| Messaging | Apache Kafka (KRaft mode, single broker locally) | Free, open-source |
| Frontend | Next.js 15 (App Router), TypeScript (strict), Tailwind CSS, TanStack Query, React Hook Form, Zod, pnpm | |
| Email (dev) | Mailpit | Free local SMTP sink |
| File storage | `local` filesystem or MinIO (S3-compatible) behind a `FileStorage` interface | |
| Containers | Docker, Docker Compose | |
| CI/CD | GitHub Actions (free tier), GHCR images | |
| Observability | Spring Actuator + Micrometer, Prometheus, Grafana, Loki (optional compose profile) | |
| AI providers | `LocalTemplateProvider` (default), `OllamaProvider` (optional free local LLM), `ClaudeProvider` (future) | Selected by `AI_PROVIDER` |

## 5. Architecture Principles

- **P1 Contract-first.** Shared behavior is defined in `docs/` before code. Code follows contracts, never the reverse.
- **P2 Modular monolith + one justified service.** The only separate backend service is `ai-service` (secret isolation, independent ownership, distinct latency/failure profile). No other microservices without an ADR approved by all three developers.
- **P3 Backend is the only public API.** Browser → Next.js → Backend. `ai-service` is internal-only.
- **P4 Server-side AI only.** No AI key, SDK, or provider URL ever exists in `frontend/`.
- **P5 Provider abstraction.** All model access is behind `AIProvider`. Local development never needs a paid API.
- **P6 Validate everything at every boundary.** Client (UX) → DTO → domain → DB constraints; AI output → JSON Schema → business rules → sanitization → persist.
- **P7 Defense in depth.** AuthN, RBAC, ownership checks, rate limits, quotas, audit.
- **P8 Async where it decouples.** Domain events via transactional outbox → Kafka; consumers are idempotent.
- **P9 Explicit module boundaries.** Cross-module calls only through a module's public facade; enforced with ArchUnit.
- **P10 Everything important is auditable.**
- **P11 Twelve-factor configuration.** Env vars only; `.env.example` committed; secrets never.
- **P12 Small, vertical, frequently-merged slices.** Long-lived divergence is the enemy.

## 6. Decision Register

| ID | Decision |
|---|---|
| D-01 | Modular monolith (`backend/`) + one separate `ai-service/`. |
| D-02 | `ai-service` is Spring Boot/Java 21, internal network only, owns schema `ai`, never reads other schemas; it receives all domain context in the request payload. |
| D-03 | Public AI endpoints (`/api/v1/ai/**`) are served by the backend's `ai` gateway package (owned by Developer 3) which authenticates, rate-limits, enriches, and proxies to `ai-service`. |
| D-04 | One PostgreSQL, schemas `core`, `community` (Dev 1), `platform` (Dev 3), `ai` (ai-service, Dev 3). Flyway runs per schema with its own history table. FK direction allowed: `community→core`, `platform→core`. Never `core→*`. `ai` has no FKs to other schemas. |
| D-05 | Auth: short-lived JWT access token (15 min, in memory) + rotating opaque refresh token (14 days, `httpOnly` cookie, hashed in DB, reuse detection). Browser talks same-origin through Next.js rewrites (no CORS in prod). |
| D-06 | Kafka KRaft; transactional outbox in `platform.outbox_events`; at-least-once delivery; idempotent consumers via `platform.processed_events`. |
| D-07 | Search = PostgreSQL full-text (`tsvector` + GIN) + `pg_trgm`, behind a `SearchProvider` interface; OpenSearch is a future drop-in. |
| D-08 | Redis used for cache, rate limits, AI quota counters, SSE pub/sub, idempotency keys, scheduler locks. Always TTL'd, always reconstructable. |
| D-09 | OpenAPI snapshot committed at `docs/openapi/openapi.v1.json` (generated by backend CI); frontend generates types from it; frontend uses MSW mocks derived from `API_CONTRACT.md` until backend endpoints exist. |
| D-10 | Git lanes: `feature/backend` (Dev 1), `feature/frontend` (Dev 2), `feature/ai` and **`feature/infra`** (Dev 3). Merge to `develop` at least daily (see ARCHITECTURE §24). |
| D-11 | UUID primary keys (app-generated), `timestamptz` UTC, enums stored as `varchar` + `CHECK`. |
| D-12 | Soft delete for user-generated/business entities (`deleted_at`); hard delete for tokens and owner-managed sub-records. Users are anonymized, not deleted. |
| D-13 | `LocalTemplateProvider` is the default and must pass the full contract test-suite; `OllamaProvider` optional; `ClaudeProvider` later. |
| D-14 | Community backend is owned by Dev 1 (Phase 4); Dev 3 supplies feed caching/search/notification hooks; Dev 2 owns Community UI. |
| D-15 | Error envelope is a custom JSON (`{ "error": {...} }`), not RFC 7807. |
| D-16 | A recruiter may publish jobs only if `recruiter_profiles.approval_status = APPROVED` **and** `companies.verification_status = VERIFIED`. |
| D-17 | One application per seeker per job; withdrawn applications cannot be resubmitted in v1. |
| D-18 | pnpm is the only JS package manager. |

## 7. Non-Negotiable Rules (Hard Constraints)

1. Frontend never contains secret API keys. 2. Frontend never calls an AI provider. 3. AI access is server-side. 4. AI is behind `AIProvider`. 5. System works locally with no paid AI. 6. AI output is schema-validated before persistence. 7. AI endpoints require authentication **and** authorization. 8. AI usage is quota-controlled. 9. Rate limiting is enforced. 10. Important operations are audited. 11. No independent redesign of shared architecture. 12. No edits to another developer's module without a documented request. 13–15. API/DB/shared changes are documented first. 16. Secrets are never committed. 17. Production-quality architecture. 18. No unnecessary microservices. 19. Realistic for three people. 20. Prefer local/open-source infrastructure.

## 8. Developer Responsibilities and Ownership

### 8.1 Ownership table

| Area | Owner | Paths |
|---|---|---|
| Backend core: auth, users, profiles, companies, jobs CRUD, applications, interviews, community, admin, audit, analytics, storage | **Dev 1** | `backend/**` **except** the Dev 3 packages below; `backend/src/main/resources/db/migration/{core,community}/**`; `docs/status/backend.md` |
| Frontend (all roles, community, AI Job Studio UI, API integration, mocks) | **Dev 2** | `frontend/**`; `docs/status/frontend.md` |
| AI abstraction/service, quota, validation, recommendations | **Dev 3** | `ai-service/**`; `docs/status/ai-infra.md` |
| Backend packages `ai` (gateway), `search`, `notification`, `platform` (events/outbox, rate-limit, cache) | **Dev 3** | `backend/src/main/java/com/jobforge/backend/{ai,search,notification,platform}/**`; `backend/src/main/resources/db/migration/platform/**` |
| Infra: Docker, Compose, CI/CD, monitoring, Redis/Kafka config | **Dev 3** | `infrastructure/**`, `docker-compose.yml`, `.github/workflows/**`, `.env.example` (shared write, see 8.3) |
| Docs (contracts) | Section owner; all approve changes | `docs/*.md`, `CLAUDE.md` |

### 8.2 Contract section ownership (`API_CONTRACT.md`)

Auth/users/profiles/companies/jobs CRUD/applications/interviews/community/admin(non-AI)/analytics → Dev 1. `GET /jobs` search + facets, notifications, AI, `/admin/ai/*` → Dev 3. Dev 2 is a mandatory reviewer of **every** contract change (as the consumer).

### 8.3 Shared-write files (merge-conflict hotspots)

`.env.example`, `docker-compose.yml`, `docs/*.md`, `CLAUDE.md`, `docs/openapi/openapi.v1.json`, `backend/src/main/resources/application*.yml`, `shared/**` kernel in backend (Dev 1 owns; Dev 3 may add only under `platform/**`). Rules: additive edits only, small PRs, rebase/merge `develop` before editing, never reformat whole files. Full register in ARCHITECTURE §25.

### 8.4 Coordination rule

To need something in another developer's area: create `docs/requests/REQ-YYYYMMDD-<slug>.md` (one file per request, avoids conflicts) addressed to the owner. Do not patch their module. Emergency exception: a minimal fix PR **reviewed by the owner**.

## 9. Development Phases

Each phase starts with a **contract PR** (endpoints, DTOs, tables, events) merged to `develop` before implementation.

| Phase | Scope | Exit criteria |
|---|---|---|
| **0 Foundation** (week 1) | Repo skeletons, compose (Postgres/Redis/Kafka/Mailpit), CI for all three, error/response kernel, OpenAPI snapshot pipeline, MSW mock setup, ArchUnit rules, CODEOWNERS | `docker compose up` works; all CI green on empty skeletons; health endpoints up |
| **1 Identity & Profiles** | AUTH-*, JS-1..3, RC-1, AD-1 (users), frontend shell/auth/profile pages, infra hardening | Register→verify→login→refresh→logout works end-to-end; RBAC + IDOR tests pass |
| **2 Jobs & Applications** | RC-2, RC-3, JS-4..6, AD-2 (jobs), search module, frontend job flows | Seeker applies to a published job; recruiter moves it through the pipeline; search <300 ms p95 on 10k seeded jobs |
| **3 Notifications, Interviews, AI Job Studio** | JS-7, RC-4, RC-5, AI-1 (gen/validate), AI-2, AD-5 (basic) | Generate→edit→publish a job using `LocalTemplateProvider`; quota and rate limits enforced; events drive notifications |
| **4 Community** | CM-1..4, AD-2 (community) | All 7 post types; follow/mention notifications; moderation queue |
| **5 Intelligence & Admin** | JS-8, RC-6, RC-7, AD-3, AD-4 | Recommendations/insights served; analytics dashboards; audit viewer |
| **6 Hardening** | Security review, load tests, monitoring dashboards/alerts, docs, a11y | Checklist in ARCHITECTURE §19 fully green |
| **7 Production & Claude** | Deployment, backups, `ClaudeProvider` enablement | ARCHITECTURE §29 checklist |

## 10. Security Requirements (summary; detail in ARCHITECTURE §19)

OWASP ASVS L2 as target. BCrypt(12) passwords; account lockout and rate limits on auth; short-lived JWT + rotating refresh; RBAC + ownership checks on every non-public endpoint; method-level `@PreAuthorize`; input validation everywhere; Markdown stored as text and rendered without raw HTML; upload type/size/magic-byte validation; security headers; parameterized queries only; no PII/secrets in logs; dependency and secret scanning in CI; internal service auth between backend and `ai-service`; least-privilege DB roles; append-only audit log.

## 11. Testing Requirements (summary; detail in ARCHITECTURE §23)

Every PR: unit tests for new logic; integration tests (Testcontainers) for new endpoints/repos including **authorization negative tests** (wrong role, wrong owner); contract tests for API shape; frontend component + MSW tests; AI schema/golden tests. Minimum coverage gates: backend service layer ≥ 70 % lines, ai-service ≥ 80 %, frontend feature logic ≥ 60 %. Merges to `main` require a green end-to-end smoke test.

## 12. Zero-Budget Constraints

- Only free/open-source tools; GitHub Actions free tier; no paid SaaS in any required path.
- Claude Web Free has limited context and message caps: sessions must be **small and focused** (one vertical slice). Developers paste the **Session Bootstrap** from `CLAUDE.md` plus only the relevant contract sections. Docs are numbered and modular for this reason.
- AI features must be fully functional with `LocalTemplateProvider`; the Claude provider is additive.
- A private GitHub repo on a free plan may lack enforceable branch protection: compensate with CODEOWNERS, PR templates, CI required checks where available, and team discipline (no direct pushes to `main`/`develop`).

## 13. Document Change Control

1. A change to `API_CONTRACT.md`, `DATABASE_SCHEMA.md`, events, or `CLAUDE.md` is a PR labelled `contract-change` containing **only** docs (and OpenAPI snapshot if applicable).
2. Approval: section owner + every affected consumer (Dev 2 always for API).
3. Merge the contract PR **before** dependent code.
4. Append a line to `docs/CHANGELOG_CONTRACTS.md` (append-only; format `YYYY-MM-DD | doc §section | summary | PR #`).
5. Breaking changes require a new API version or an expand/contract migration plan (API_CONTRACT §2).

Supporting files to create in Phase 0: `docs/CHANGELOG_CONTRACTS.md`, `docs/status/{backend,frontend,ai-infra}.md`, `docs/requests/.gitkeep`, `docs/adr/0000-template.md`, `.github/CODEOWNERS`, `.github/pull_request_template.md`, `.env.example`.
