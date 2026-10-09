# REQ-20261009 — Contract additions made in revision 3

To: contract owners (Dev 1 backend, Dev 2 API consumer, Dev 3 platform). Urgency: before merge to `develop`.
Supersedes nothing; extends REQ-20261008-contract-additions.md. A `contract-change` PR is required.

## 1. PATCH can clear optional text fields (API_CONTRACT §12.2)
`PATCH /seekers/me/education/{id}` and `/experience/{id}`: field omitted or `null` = unchanged (unchanged rule);
**empty/blank string = clear** for `fieldOfStudy, grade, description` (education) and `location, employmentType, description` (experience).
Required fields still reject blank. Dates cannot be cleared except experience `current=true` (clears `endDate`).

## 2. Account deletion policy (API_CONTRACT §12.2, `DELETE /users/me`)
Returns `422 BUSINESS_RULE_VIOLATED` when the caller is a company OWNER and either (a) the company still has other members or
(b) it has open jobs. Otherwise the company membership is released and the account anonymized. Applications and notes are kept.
ADMIN accounts remain refused.

## 3. UPLOAD rate class (ARCHITECTURE §15)
10 requests/minute per user (IP if anonymous) on `PUT /users/me/avatar`, `PUT /companies/{id}/logo`, `POST /seekers/me/resumes`.
Exceeded: `429 RATE_LIMITED` + `Retry-After`. Fixed one-minute window in Redis (`jf:{env}:rl:upload:{subject}:{minute}`, TTL 120 s);
fails open if Redis is down. Env: `RATE_LIMIT_ENABLED`, `RATE_LIMIT_UPLOAD_PER_MINUTE`.

## 4. Public company page by id
Frontend route `/companies/{id}` uses `GET /companies/{id}` and `GET /jobs?companyId=`. No by-slug read exists; adding
`GET /companies/by-slug/{slug}` is optional and not required by the UI.

## 5. Notifications deep link
Backend sets no `data.deepLink`. Frontend derives targets from `data.applicationId` / `data.jobId` and only follows safe
relative paths. If a backend `deepLink` is added later it must be a same-origin path starting with a single `/`.

## 6. Still undocumented from earlier revisions
Endpoints listed in REQ-20261008 (audit logs, admin create, avatar, logo, delete account) need their §12 rows.
