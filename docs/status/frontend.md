# Frontend status


## Revision 3 (unverified, nothing built or run)
- Done: profile editor + completeness card; edit forms for education/experience; apply form (primary preselect, /profile link); company edit (If-Match) + logo upload; public /companies/[id]; /account (avatar, details, delete account); /notifications + bell deep links; /users/[id]; MentionInput; MSW handlers + MockBootstrap; Vitest tests for new code.
- Not done: run pnpm gen:api and replace hand-written types; markdown safety re-audit; MSW node server for tests; component tests for admin pages, profile forms, company forms; Playwright flows; wire MentionInput into community when it exists.

## Revision 4 (unverified, nothing built or run)
- Done: admin recruiter and company detail pages (/admin/recruiters/[id], /admin/companies/[id], linked from approvals); clearEndDate in history edit forms; MSW node server (tests/msw-node.ts, useMockServer) + AdminPages.test.tsx using the real lib/api client; mock handler GET /admin/recruiters/:userId.
- Not done: gen:api, ProfileForm/CompanyEditForm/LogoUpload/AvatarUpload tests, Playwright flows, axe pass.

## Revision 8 (unverified, nothing built or run)
- Done: accessible `useReasonDialog` (native modal `<dialog>`) replaces `window.prompt` on all admin pages (users, approvals, jobs, company detail, recruiter detail); `ReasonDialog.test.tsx`.
- Not done: component tests for ProfileForm, HistorySections, CompanyEditForm, LogoUpload, AvatarUpload, MentionInput; Playwright flows; axe pass.

## Session 0 stabilization (unverified, nothing built or run)
- Fixed: JobSearch no longer sends `currency` (query or URL) unless a salary bound is set (backend treats it as a hard filter).
- Fixed: `JobDetail` type now matches backend `JobDetailView` (skills `{name, slug, required}[]`, optional `location`); public job page renders skill objects and tolerates missing location.
- Fixed: `RecruiterJobSummary` now matches `RecruiterJobItem` (no `company`); My jobs page no longer reads `job.company.name`, shows application count.
- Fixed: job edit page uses `buildJobPayload`, maps skill objects, null-safe location/salary, converts `datetime-local` <-> UTC ISO (`isoToLocalInput`/`localInputToIso`), keeps skill required flags, sends `''`/`null` to clear fields, no longer sends `aiRequestId` (not patchable). New `JobPatch` type; `JobUpsert` uses `SalaryInput` and optional requirements/benefits.
- Added Publish for UNPUBLISHED/CLOSED/EXPIRED jobs (My jobs and edit pages); `location?.city` in JobCard.
- Not done: typecheck/lint of these edits; tests for edit page.

### Session 0 (continued) — additional static changes (nothing built or run)
- Fixed `SaveJobButton`: `onClick={() => void toggle}` never invoked the handler; now `toggle()`. Prop narrowed to `Pick<JobSummary,'id'|'saved'>`.
- `Salary.min/max` are nullable (backend `SalaryView(Long, Long, …)`); JobCard and job detail render open-ended ranges without crashing. JobCard save toggle no longer leaks unhandled rejections.
- `Application.job` is now `ApplicationJob` (backend `JobLiteView`: id, title, company, location); `ApplicationNote.author` added.
- Static check: all `@/` and relative imports under `src` resolve; applications API client matches backend routes, `Idempotency-Key`/`If-Match` usage and query whitelists.

## Session 1 - Interviews (not built/run)
Recruiter and seeker interview pages, schedule/reschedule/cancel/complete/respond UI (RHF+Zod, TanStack Query, lib/api only), time-zone helpers; tests interview-time, interview-schema, InterviewCard. See HANDOFF revision 10.
