# Frontend Status — Phase 2

## Completed in this frontend slice

- Greenfield Next.js App Router foundation.
- Centralized API client with in-memory access token and single-flight refresh retry.
- Public job search/detail UI aligned to `GET /jobs` and `GET /jobs/{id}`.
- Seeker saved jobs.
- Seeker application submission with UUID `Idempotency-Key`.
- Seeker application list/detail and documented withdrawal window.
- Recruiter job creation draft.
- Recruiter application list and documented application-status transitions.
- React Testing Library/Vitest test foundation.

## Contract constraints preserved

- Browser calls same-origin `/api/v1/**` and Next rewrites to the backend.
- Refresh request includes `X-Requested-With: JobForge`.
- Access token is never persisted in localStorage/sessionStorage.
- `If-Match` is sent for versioned application/job writes.
- No AI SDK, AI provider URL, or AI secret is present in frontend code.
- Markdown is displayed as text/preformatted content rather than injecting raw HTML.
