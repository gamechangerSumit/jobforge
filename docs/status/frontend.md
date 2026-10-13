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

## Session 2B - Interviews frontend (static audit + gap fill; nothing built, run or tested)
**Starting point:** the uploaded repository already contained the interview frontend recorded above as "Session 1 - Interviews" (types, `lib/api/interviews.ts`, RHF+Zod form, TanStack Query hooks, recruiter and seeker pages, card with respond/cancel/complete/reschedule, `ApplicationInterviewsPanel`, time helpers, three tests, one Playwright spec). 2B did not rewrite it. It read the code against API_CONTRACT §12.7 and `docs/status/backend.md` (Session 2A) and closed the gaps below. Backend untouched.

**Requirement check**
- Recruiter management page `/recruiter/interviews` (+ `/recruiter/interviews/[id]`, panel on `/recruiter/applications/[id]`) and seeker pages `/interviews`, `/interviews/[id]`: present, role layouts use `RoleGuard` (UX only; the API enforces access).
- Schedule and reschedule form (`InterviewForm`, RHF + Zod, server field errors mapped), respond (confirm/decline with note), cancel (reason dialog), complete/no-show: present. All calls go through `lib/api/interviews.ts` and TanStack Query hooks; no `fetch` in components.

**Changed in 2B**
- New `features/interviews/InterviewState.tsx`: shared loading (`role="status"`), error panel with **Try again**, and `isNotFound`. Used by the list, detail and application panel. Before, load errors had no retry. A 404 on the detail page stays "not found or unavailable" without a retry (the API answers 404 for missing and foreign ids alike). Detail page also got a back link.
- `InterviewListView`: retry on error, server message shown, `data-testid` on the empty state.
- `lib/validation/interviews.ts` `buildUpdatePayload`: scheduled time is compared at minute precision. Previously an interview stored with seconds looked "changed" on an unchanged save, sending `scheduledAt` and resetting the candidate's answer.
- New MSW handlers `src/mocks/interviewHandlers.ts` (registered in `mocks/handlers.ts`, reset by `resetMockState`, role via new `setMockRole`): list with query whitelist, detail, schedule, patch (reschedule resets answer, notes-only edit does not), cancel, respond, complete; role split, seeker view without `seeker`/`notes`, 400/403/404/409/422 shapes as in the contract.
- New tests (source only, not executed): `tests/components/InterviewListView.test.tsx`, `InterviewDetailView.test.tsx`, `InterviewForm.test.tsx`, `ApplicationInterviewsPanel.test.tsx`, `InterviewActions.test.tsx`; helpers in `tests/interview-test-utils.tsx`; one new case in `tests/unit/interview-schema.test.ts`. They use the real `lib/api` client against the MSW node server. Existing `InterviewCard.test.tsx`, `interview-time.test.ts`, `tests/e2e/interviews.spec.ts` kept.
- Static check done: every `@/` and relative import in the new and changed files resolves. No typecheck, lint, test or build was run.

**Not completed / remaining**
- Run, in order: `pnpm typecheck`, `pnpm lint`, `pnpm test` (new component tests are unverified; selectors such as the reason `<dialog>` and the `Confirm`/`Confirmed` button names may need small fixes), then `pnpm test:e2e` for `interviews.spec.ts`.
- `Interview` types are hand-written (API_CONTRACT §12.7 prose + backend views); replace with generated types after `pnpm gen:api`.
- Time zone is a free text IANA field (validated); a zone picker would be friendlier.
- A seeker whose interview was declined, cancelled or started sees no action and no explanation beyond the status badge; copy is a design decision.
- Calendar view, ICS export and reminders are not in the contract and were not built. Notification wording for interview events is owned by the notification module.
- Backend carry-forwards from 2A (no audit for reschedule/respond/complete, non-atomic overlap check, admin read access) are unchanged.

### Session 2B follow-up (still not built, run or tested)
- Time zone field now suggests IANA zones through a `<datalist>` (`timeZoneOptions()` in `lib/interviews/time.ts`, browser list with a small fallback); free text is still validated by Zod. Tests: `interview-time.test.ts`, `InterviewForm.test.tsx`.
- Seekers now get a plain-language note when they have no buttons (cancelled, over, declined, already started); closed applications keep the existing banner. Test in `InterviewCard.test.tsx`.
- Still open: run typecheck/lint/test/e2e (not allowed in these sessions); replace hand-written types after `pnpm gen:api`; calendar view, ICS export and reminders (not in the contract, not invented).
