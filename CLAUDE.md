# CLAUDE.md — Permanent Instructions for Any Claude Session in the JobForge Repository

You are a development assistant working for **one** of three human developers on JobForge (Java/Spring Boot backend, Next.js frontend, AI service + infrastructure). You do **not** share memory with the other developers' Claude sessions. **The repository documents are the only shared memory.** Obey them over your own preferences, habits, or assumptions.

---

## 0. Session Bootstrap (developer pastes this block at the start of every Claude Web Free chat)

```
SESSION BOOTSTRAP
Lane: <backend | frontend | ai | infra>        Developer: <Dev 1 | Dev 2 | Dev 3>
Task: <one vertical slice, e.g. "RC-2 publish job endpoint">
Feature IDs: <from MASTER_SPEC §3>
Attached: CLAUDE.md + only the relevant sections of API_CONTRACT / DATABASE_SCHEMA / ARCHITECTURE (cite § numbers)
Current branch: <feature/...>   Last status file: <paste docs/status/<lane>.md>
Rules: follow CLAUDE.md. Do not change contracts silently. Ask or write a REQ file if something is missing.
```

If the bootstrap or the needed contract sections are missing, **ask for them before writing code**. Do not guess endpoint shapes, table columns, event payloads, or error codes.

Free-tier efficiency: work in small vertical slices; prefer complete files or clearly delimited diffs over long prose; do not re-print unchanged code; state which file path each code block belongs to; stop and summarize if the context is getting long (see §13).

## 1. Source of Truth and Precedence

1. `docs/DATABASE_SCHEMA.md` — tables, enums, constraints, indexes.
2. `docs/API_CONTRACT.md` — endpoints, DTOs, errors, AI contracts.
3. `docs/ARCHITECTURE.md` — structure, flows, Kafka/Redis/security rules, git workflow, ownership.
4. `docs/MASTER_SPEC.md` — intent, decisions (D-xx), phases.
5. This file.
If code and docs disagree, the docs win unless a contract-change PR says otherwise. If docs disagree with each other, stop and report it.

## 2. Identify Your Lane — and Stay in It

| Lane | Developer | You MAY edit | You MUST NOT edit (request instead) |
|---|---|---|---|
| backend | Dev 1 | `backend/**` except the Dev 3 packages; `db/migration/{core,community}`; `docs/status/backend.md` | `backend/.../{ai,search,notification,platform}/**`, `db/migration/platform`, `ai-service/**`, `frontend/**`, `infrastructure/**`, `.github/workflows/**` |
| frontend | Dev 2 | `frontend/**`; `docs/status/frontend.md` | everything else |
| ai | Dev 3 | `ai-service/**`; `backend/.../ai/**`; `docs/status/ai-infra.md` | `backend` core modules, `frontend/**` |
| infra | Dev 3 | `infrastructure/**`, `docker-compose.yml`, `.github/**`, `backend/.../{search,notification,platform}/**`, `db/migration/platform` | backend core modules, `frontend/**` |

Shared-write files (additive, tiny diffs only): `.env.example`, `docs/*.md` (via contract PR), `docs/CHANGELOG_CONTRACTS.md`.
If the task requires touching something outside your lane: **do not do it**. Create `docs/requests/REQ-YYYYMMDD-<slug>.md` addressed to the owner (what, why, proposed contract, urgency) and continue with what you can do (stubs/mocks).

## 2. Hard Rules (never violate; refuse politely if asked to)

1. No secret/API key in `frontend/`, in Git, in logs, in tests, or in examples. Use env vars and placeholders only.
2. The frontend never calls an AI provider; it only calls `/api/v1/**` on the backend.
3. AI provider access exists only in `ai-service` behind the `AIProvider` interface.
4. Everything must run locally with **no paid service**. `LocalTemplateProvider` is the default provider.
5. AI output is JSON-Schema-validated and business-validated **before** persistence; failures are not stored as output and don't consume credits.
6. AI endpoints require authentication, role authorization, rate limiting, and quota; generation endpoints require `Idempotency-Key`.
7. Important operations are audited (DATABASE_SCHEMA §13).
8. Do not redesign shared architecture, rename contract fields, change error codes, or add services/datastores. Propose via contract change / ADR.
9. Do not modify another lane's module. Use REQ files.
10. No unnecessary microservices; no new infra dependencies without an ADR.
11. Never weaken security for convenience (no `permitAll` shortcuts, no disabling CSRF/CORS/validation, no logging tokens/PII, no string-built SQL).
12. Never edit an already-applied Flyway migration; never write a migration without the matching DATABASE_SCHEMA change merged.

## 3. Contract Change Protocol (when the docs are insufficient or wrong)

1. Stop implementing the affected part.
2. Draft the exact doc change (section number, old → new) and say who must approve (section owner + Dev 2 for API).
3. Output it as a **docs-only** change for a PR labelled `contract-change`, plus the `CHANGELOG_CONTRACTS.md` line (`YYYY-MM-DD | doc §section | summary | PR #`).
4. Only after it is merged to `develop`, implement against it.
Never "just add a field" in code and update the docs later.

## 4. Git and PR Rules

- Branches: `main`, `develop`, `feature/backend`, `feature/frontend`, `feature/ai`, `feature/infra`. Work on your lane branch or a flat `feature/<lane>-<topic>` branch. **Never** create `feature/<lane>/<topic>` (Git ref conflict).
- Merge `develop` into your lane at session start; PR lane → `develop` at least daily; merge commits (no squash) for lane→develop.
- Conventional Commits with module scope and feature ID: `feat(application): withdraw endpoint (JS-6)`.
- Never push to `main`/`develop` directly; never force-push shared branches.
- PR checklist (reproduce in the PR description): contract updated · migration added · tests added incl. authorization negatives · OpenAPI snapshot regenerated (backend) · `.env.example` updated · docs/status updated.
- Provide a short **PR summary** and **commit message(s)** at the end of every slice.

## 5. Backend Conventions (Dev 1 / Dev 3 backend packages)

- Java 21, Spring Boot 3.x, Maven. Root package `com.jobforge.backend`; module layout `api / facade / app / domain / infra / events` (ARCHITECTURE §4). Cross-module calls only via `facade`. ArchUnit tests must pass.
- DTOs ≠ entities. Controllers thin; transactions in `app` services. Constructor injection. `Clock` injected (no `Instant.now()` in domain logic).
- Authorization = URL rules + `@PreAuthorize` + ownership policy; not-visible → 404. Every endpoint ships `*AuthorizationIT` tests (401/403/IDOR).
- Responses use the envelope (API_CONTRACT §4); errors only via typed exceptions → `GlobalExceptionHandler`; error codes only from API_CONTRACT §5.1.
- Pagination/sorting/filter whitelists per API_CONTRACT §6–8. Idempotency/If-Match where specified.
- Persistence: Flyway per schema, timestamp-versioned, schema rules in DATABASE_SCHEMA §2/§16. `@Version` on versioned entities. Soft delete via `deleted_at` + `@SQLRestriction`.
- Events: only through `OutboxPublisher`; envelope per ARCHITECTURE §14; consumers idempotent via `processed_events`. Never use `KafkaTemplate` directly in feature code.
- Redis only through the `platform` abstractions (`RateLimiter`, `CacheService`, idempotency); key pattern `jf:{env}:{domain}:…`, TTL mandatory.
- Logging: SLF4J, structured, no PII/secrets. Config via `@ConfigurationProperties`, env vars only.
- Tests: JUnit 5, Mockito, Testcontainers (no H2).

## 6. Frontend Conventions (Dev 2)

- Next.js App Router, TypeScript `strict` (no `any`), Tailwind, TanStack Query, React Hook Form + Zod, pnpm only.
- All HTTP through `lib/api`; types from `docs/openapi/openapi.v1.json` (`pnpm gen:api`); never hand-write server DTO types that already exist.
- Zod limits mirror API_CONTRACT §10. Map `error.code` / `details[].field` to UI/form errors.
- Access token in memory only; refresh via cookie flow (ARCHITECTURE §8); never store tokens in localStorage/sessionStorage.
- Role route groups are UX guards only. Render Markdown without raw HTML. Accessibility: labels, focus, keyboard, axe clean.
- Build against **MSW mocks that match API_CONTRACT** until the real endpoint exists; mocks live in `frontend/src/mocks`; keep them in sync.
- Only `NEXT_PUBLIC_*` env vars in browser code, never secrets. No AI SDKs/provider URLs in `frontend/`.
- Tests: Vitest + RTL + MSW; Playwright for critical flows.

## 7. AI Service Conventions (Dev 3)

- All model access through `AIProvider`; implementations: `LocalTemplateProvider` (default), `OllamaProvider` (optional), `ClaudeProvider` (future, disabled unless `AI_PROVIDER=claude`). Model ids/keys from env; never hard-code.
- Pipeline order: internal auth → quota reserve → prompt registry (versioned, data-slot delimiting) → provider → parse → JSON Schema validate → business validation/quality score → persist → commit quota → audit/event → respond.
- Treat all user-supplied text as **data**, never instructions. Strip HTML/URLs/emails from outputs per schema rules.
- Quota in Redis (atomic Lua) **and** ledger in PG (source of truth). Failed/rejected = no charge. Cache hits = no charge.
- ai-service never reads `core`/`community`/`platform`; it receives context from the backend. `ai` schema has no FKs outside itself.
- The same provider contract test-suite must pass for every provider; `LocalTemplateProvider` must pass 100 %.
- Internal endpoints per API_CONTRACT §15; authenticate via `X-Internal-Token` (constant-time compare).

## 8. Infrastructure Conventions (Dev 3)

- Compose profiles: default (infra), `app`, `tools`, `monitoring`, `storage`, `ollama`, `security`. Healthchecks + `depends_on: service_healthy`. Non-root multi-stage Dockerfiles. No hard-coded secrets; `.env.example` placeholders only.
- CI is path-filtered per lane; required checks per ARCHITECTURE §22. Keep workflows free-tier friendly (cache dependencies, avoid heavy matrices).
- Use only free/open-source images and tools.

## 9. Security Checklist for Every Slice

Does this endpoint/page need authN? Which roles? Is ownership checked (IDOR)? Is input validated and size-limited? Does it write an audit record? Does it leak data in errors/logs? Is it rate-limited? Any secret introduced? Any new dependency (justify, check license, free)? If uncertain, choose the more restrictive option and say so.

## 10. Definition of Done

Code + tests (unit, integration, authorization negatives) · lint/format clean · contract/doc updates merged first · OpenAPI snapshot regenerated (backend) · `.env.example` updated · migration + schema doc aligned · CI green locally where possible · PR summary written · `docs/status/<lane>.md` updated.

## 11. How to Respond in a Session

1. Restate the slice and cite the contract sections you are implementing (e.g. "API_CONTRACT §12.6, DATABASE_SCHEMA §4.5").
2. List any missing information or conflicts **first**; do not code around them silently.
3. Provide the work as files with explicit paths, in dependency order (migration → domain → service → controller → tests; or types → api → hooks → components → tests).
4. Include tests with the code. Include the commands to run them.
5. End with: **Files changed**, **Docs/contract impact** (none, or the exact change), **REQ files needed**, **PR summary + commit message**, **status-file update text**.
Keep answers concise; do not repeat the contracts back.

## 12. Things You Must Not Do Even If Asked

Commit or print real secrets · call or embed a paid API in required code paths · put an AI key/provider call in the frontend · skip authorization or validation "for now" · edit another lane's files directly · change enums/error codes/field names ad hoc · rewrite applied migrations · introduce a new service, queue, or database · disable tests or CI checks to go green. If the developer insists, explain which rule blocks it and offer the compliant path (contract change, REQ file, ADR).

## 13. Context Management for Free-Tier Sessions

- One slice per chat. When responses start to degrade or the chat is long, produce a **handoff block**: done / decisions / open questions / next steps / files touched — formatted to paste into `docs/status/<lane>.md` and the next session's bootstrap.
- When asked about contracts you haven't been given, request the section rather than recalling from memory.
- Prefer referencing sections (`API_CONTRACT §12.4`) over quoting them.

## 14. Quick Reference (stable facts)

- Base path `/api/v1`; envelope `{data, meta}` / `{error}`; IDs UUID; times UTC ISO-8601; enums UPPER_SNAKE_CASE; JSON camelCase.
- Ports: frontend 3000 · backend 8080 · ai-service 8090 · Postgres 5432 · Redis 6379 · Kafka 9092 · Mailpit 1025/8025 · MinIO 9000/9001 · Kafka UI 8081 · Prometheus 9090 · Grafana 3001.
- Schemas: `core`, `community` (Dev 1) · `platform` (Dev 3) · `ai` (ai-service).
- Roles: `JOB_SEEKER`, `RECRUITER`, `ADMIN`. Post types: `DISCUSSION, HIRING, REFERRAL, PROJECT, CAREER_ADVICE, TECHNICAL, EVENT`.
- Auth: access JWT 15 min (memory) + rotating refresh cookie `jf_refresh` (14 d, httpOnly, SameSite=Strict).
- Rate classes: AUTH, DEFAULT, SEARCH, WRITE_COMMUNITY, AI, UPLOAD. AI credits: generation 5, validation 1, job recs 1, candidate recs 2, insights 2.
- Kafka topics: `jobforge.{users,jobs,applications,interviews,community,moderation,ai,audit}.v1` (+ `.dlq`).
- Redis key pattern: `jf:{env}:{domain}:{…}`.
