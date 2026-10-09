# REQ-20261008 - Contract additions implemented in code (needs contract-change PR)

Implemented ahead of the docs; per CLAUDE.md section 3 these must be merged into API_CONTRACT and CHANGELOG_CONTRACTS.md.

| Endpoint | Auth | Notes |
|---|---|---|
| GET /users/{id}/avatar | public | Image bytes; public because img tags cannot send the in-memory bearer token. Cache-Control public 1h, nosniff. |
| PUT /companies/{id}/logo | RECRUITER (company OWNER) | multipart `file`, PNG/JPEG/WEBP, max 2 MB, magic bytes verified. |
| GET /companies/{id}/logo | public | Same rationale as avatar. |
| POST/publish, unpublish, close on /jobs/{id} | RECRUITER | Now honor optional If-Match (STALE_VERSION on mismatch). |
| POST /admin/users | ADMIN | Created ADMIN gets a set-password link (reuses PASSWORD_RESET token, 24h TTL). Audit ADMIN_CREATED. |
| GET /admin/audit-logs | ADMIN | Filters actorId, action, entityType, entityId, from, to; offset paging. |
| DELETE /users/me | any except ADMIN | Body {password}; anonymizes user, scrubs seeker profile, revokes sessions, audit USER_DELETED. |

New audit actions: ADMIN_CREATED, USER_AVATAR_CHANGED, USER_DELETED, COMPANY_LOGO_CHANGED (DATABASE_SCHEMA section 13).
