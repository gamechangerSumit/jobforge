# AI trusted context and privacy boundaries

JobForge builds AI context in the backend after authenticating the caller. Browser-supplied context is never trusted.

## Seeker career insights and job recommendations

- Caller must be authenticated as `JOB_SEEKER`.
- Backend loads only that caller's seeker profile through `SeekerDataFacade`.
- Backend loads up to 10 currently public jobs using the search module facade and the seeker's skills/location.
- Context includes professional headline/summary, current title, years of experience, profile completeness, skills and coarse location.
- Phone, expected salary, resume bytes/storage keys, account email, user identifiers and application notes are excluded.
- The AI service strips secret-like keys and removes the entire `context` object from persisted request input. Context is transient provider input only.

## Recruiter candidate matching

- Caller must be an approved recruiter and must belong to the company that owns the selected job.
- Job must be currently accepting applications and publicly visible.
- Backend fetches only applications to that specific job, excludes terminal/closed applications, and loads profiles through `SeekerDataFacade`.
- Profiles with `PRIVATE` visibility are excluded. `PUBLIC` and `RECRUITERS_ONLY` profiles can be included for the owning recruiter.
- Context contains an ephemeral candidate reference, professional headline/summary, current title, experience, completeness and skill names. Profile and application UUIDs stay in the backend and are joined to model results only after the AI call.
- Phone, expected salary, email, resume content, private profile content and account secrets are excluded.
- The AI service does not persist the trusted context. The result carries the application id so the recruiter can review the original application.

## Decision support

Match scores are deterministic skill-overlap signals in the local-template provider. They are not probability-of-hire estimates, ranking guarantees, or automated hiring decisions. Recruiters must review applications manually and must not use scores as the sole basis for adverse decisions.

## Operational checks

- Keep the AI internal token server-side.
- Keep `OPENAPI_ENABLED=false` outside contract-generation environments.
- Do not log raw provider inputs or trusted context.
- Review data retention and deletion behavior before production launch.
