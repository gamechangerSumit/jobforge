# Contract Changelog
Format: `YYYY-MM-DD | doc §section | summary | PR #` (PR numbers filled when the contract-change PR opens)

2026-10-08 | API_CONTRACT §12.11 | GET /admin/audit-logs; POST /admin/users (creates ADMIN, set-password mail) | TBD
2026-10-08 | API_CONTRACT §12.2 | PUT /users/me/avatar, GET /users/{id}/avatar (public), DELETE /users/me (password-confirmed anonymization) | TBD
2026-10-08 | API_CONTRACT §12.3 | PUT /companies/{id}/logo (owner), GET /companies/{id}/logo (public) | TBD
2026-10-08 | API_CONTRACT §12.4 | If-Match honored on job publish/unpublish/close | TBD
2026-10-09 | API_CONTRACT §12.2 | PATCH education/experience: blank string clears optional text fields | TBD
2026-10-09 | API_CONTRACT §12.2 | DELETE /users/me returns 422 BUSINESS_RULE_VIOLATED for company owners with members or open jobs | TBD
2026-10-09 | ARCHITECTURE §15 | UPLOAD rate class: 10/min on avatar, logo, resume upload; 429 + Retry-After | TBD
2026-10-09 | API_CONTRACT §12.4 | GET /jobs and GET /jobs/{id}/similar fill saved/applied for signed-in JOB_SEEKER callers (null for anonymous/other roles) | TBD
2026-10-10 | API_CONTRACT §12.3 | GET /admin/recruiters/{userId} (admin recruiter detail) | TBD
2026-10-10 | API_CONTRACT §12.2 | PATCH education/experience accept clearEndDate to remove a stored endDate | TBD
2026-10-10 | ARCHITECTURE §21 | compose profiles tools/monitoring/storage/ollama; backend healthcheck; optional MANAGEMENT_PORT for metrics (micrometer-registry-prometheus added) | TBD
2026-10-11 | API_CONTRACT §12.7 | Interview response object, state/scheduling rules, admin gets 403, complete endpoint rules, list filters/sort, events and notification mapping documented (no request-shape change) | TBD
2026-10-12 | API_CONTRACT §12.7 | GET /interviews accepts applicationId; open interviews are auto-cancelled when the application is withdrawn/rejected (event-driven, InterviewCancelled.cause) | TBD
2026-10-10 | API_CONTRACT §12.9, §12.11; ARCHITECTURE §14 | Reports/moderation: POST /reports and /admin/reports DTOs and §4 envelopes, phased target/action support (POST/COMMENT and HIDE_CONTENT unsupported), resolve reason 10–500 after trimming, privacy boundaries, DEFAULT rate class, ContentReported/ContentModerated consumer mapping | TBD
2026-10-10 | API_CONTRACT §12.9.1, §12.11.1 | POST /reports 201 sets Location: /api/v1/admin/reports/{id} (ADMIN-readable only; no reporter read endpoint in v1); resolve reason trim set clarified (Unicode whitespace incl. NBSP/BOM) and counted in code points | TBD
2026-10-10 | API_CONTRACT §12.9.1, §12.11.1 | Clarified: report details limit is 1000 UTF-16 code units (no behavior change); Location URI is an admin resource (no reporter read endpoint in v1); v1 decisions recorded: DEFAULT rate class, admin report notifications deferred, POST/COMMENT targets and HIDE_CONTENT deferred to Community session | TBD
2026-10-10 | API_CONTRACT §12.9.1 | Clarified report details normalization: raw 1000 UTF-16 code-unit check before trimming, then ECMAScript-whitespace trim (same set as resolve reason); empty after trim = omitted | TBD
