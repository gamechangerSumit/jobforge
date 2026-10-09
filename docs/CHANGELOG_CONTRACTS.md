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
