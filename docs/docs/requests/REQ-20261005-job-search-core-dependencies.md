# REQ-20261005 — Job Search Core Dependencies

**From:** Dev 3 (infra/search)
**To:** Dev 1 (backend)
**Urgency:** Before Job Search integration testing
**Status:** requested; no contract text changed

## 1. Purpose

Dev 3 is implementing the Phase 1 public Job Search slice:

GET /api/v1/jobs

The API contract assigns this endpoint to Dev 3.

## 2. Required Dev 1 database dependency

The current backend branch does not yet contain the core migrations for:

- core.companies
- core.jobs
- core.skills
- core.job_skills

The Job Search implementation depends on these tables exactly as defined by:

DATABASE_SCHEMA.md §4.3
DATABASE_SCHEMA.md §4.4
DATABASE_SCHEMA.md §9

Please add/merge the corresponding Dev 1 core migrations before integration testing.

Dev 3 will not modify db/migration/core.

## 3. Required CompanyFacade dependency

ARCHITECTURE.md §4 requires cross-module access through facades.

Job Search returns the API_CONTRACT §11 JobSummary, which contains:

company.id
company.name
company.slug
company.logoUrl
company.verified

Please expose the company summary through the existing/new:

CompanyFacade

The architecture currently specifies:

CompanyFacade.getSummary(id)

If a batch form is preferred for search-page efficiency, please expose an additive batch facade method such as:

CompanyFacade.getSummaries(Collection<UUID> ids)

No HTTP or database contract change is requested.

## 4. Search read-model boundary

Dev 3 search will directly query only the sanctioned read-model tables:

- core.jobs
- core.job_skills
- core.skills

The search implementation will not directly access arbitrary Dev 1 repositories.

## 5. Public-search requirements

GET /api/v1/jobs must:

- be public
- return only status=PUBLISHED
- exclude deleted jobs
- support q
- support location
- support country
- support workMode
- support employmentType
- support experienceLevel
- support salaryMin
- support salaryMax
- support currency
- support skills with AND semantics
- support companyId
- support postedWithin
- support page
- support size
- support sort=relevance|postedAt|salary

## 6. No contract change

No API contract change is requested.

No DATABASE_SCHEMA change is requested.

This REQ only identifies implementation dependencies required to consume the already-approved contracts.

## 7. Requested action

Please provide/merge:

1. The required core job/company/skill migrations.
2. CompanyFacade summary access required by Job Search.

After those are available, Dev 3 can complete the JDBC provider and integration tests.