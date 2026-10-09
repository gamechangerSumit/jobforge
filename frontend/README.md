# JobForge Frontend

Phase 2 frontend for JobForge: jobs, saved jobs, applications, and recruiter application pipeline.

## Stack

- Next.js App Router + TypeScript strict
- Tailwind CSS
- TanStack Query
- React Hook Form + Zod
- Vitest + React Testing Library + MSW
- Playwright for critical flows

## Local setup

```bash
pnpm install
cp .env.example .env.local
pnpm dev
```

The browser uses same-origin `/api/v1/**`; Next.js rewrites those requests to `BACKEND_INTERNAL_URL`. The access token is kept in memory only. Refresh uses the `jf_refresh` cookie and `X-Requested-With: JobForge`.

## API types

The repository's generated OpenAPI file is the source of truth. When `docs/openapi/openapi.v1.json` is available, run:

```bash
pnpm gen:api
```

Do not hand-edit generated API types.

## Mocking

MSW handlers live in `src/mocks`. Tests use the same API envelope defined by `API_CONTRACT.md`. Generate the browser worker once with:

```bash
pnpm mock:init
```

## Validation

```bash
pnpm typecheck
pnpm lint
pnpm test
pnpm build
```

## Phase 2 flows

- Public job search and job detail
- Seeker save/unsave
- Seeker application submission with `Idempotency-Key`
- Seeker application history and withdrawal
- Recruiter job creation draft
- Recruiter application list and contract-defined status transitions

No frontend code calls `ai-service` directly.
