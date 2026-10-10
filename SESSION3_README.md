# Session 3 module - Reports & Moderation (3A backend + 3B frontend)

Static work only: nothing here was compiled, built, linted, started or tested.

## How to apply
1. Copy the `jobforge/` folder of this zip over your repo root (same paths). 
2. The 4 files in `docs-append/` are NOT drop-in: open the matching file under `docs/status/` (and `API_CONTRACT.md` / `CHANGELOG_CONTRACTS.md` if you approve the optional contract text) and paste the block at the END. Do not overwrite those docs (the 3A copy of backend.md was based on Session 0 and would delete the interview notes).
3. `git status` should show 13 modified and 38 new files (lists below).

## IMPORTANT merge notes
- `AuditAction.java`, `EventTopics.java` and `NotificationKafkaConsumer.java` in this zip are MERGED versions: the interview-session versions plus the 3A additions. Do not replace them with the 3A zip copies (they would drop the interview events/notifications).
- If your repo has other implementers/mocks of `JobFacade` or `CompanyAccessFacade` they must implement `reportView` / `ownerOf` (the new methods) or the build fails.
- No database migration: `core.reports` and `core.moderation_actions` already exist in `V202610070100__core_interviews_reports.sql`.

## Test-infrastructure fix you asked about earlier (HealthEndpointIT: invalid value for parameter "TimeZone": "Asia/Calcutta")
Cause: your Windows JVM zone is the legacy alias `Asia/Calcutta`; the Postgres 16 container rejects it. `JobForgeApplication.main` forces UTC, but tests never run `main` and `TestDatabase` connects first. Minimal fix (not applied here to keep this session's scope): in `backend/src/test/java/com/jobforge/backend/support/TestDatabase.java` add inside the class

    static { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")); }

Alternative without code: IntelliJ -> Run configuration -> VM options `-Duser.timezone=UTC` (or Asia/Kolkata).

## Security note
`.env.example` in the zip you sent earlier contained real-looking secrets (DB/Redis passwords, JWT secret). Treat them as exposed; rotate if ever pushed and keep CHANGE_ME placeholders.

## NEW files (38)
- `backend/src/main/java/com/jobforge/backend/report/api/AdminReportController.java`
- `backend/src/main/java/com/jobforge/backend/report/api/ReportController.java`
- `backend/src/main/java/com/jobforge/backend/report/app/ReportEvents.java`
- `backend/src/main/java/com/jobforge/backend/report/app/ReportRepository.java`
- `backend/src/main/java/com/jobforge/backend/report/app/ReportService.java`
- `backend/src/main/java/com/jobforge/backend/report/app/ReportTargetPort.java`
- `backend/src/main/java/com/jobforge/backend/report/app/ReportViews.java`
- `backend/src/main/java/com/jobforge/backend/report/domain/ModerationAction.java`
- `backend/src/main/java/com/jobforge/backend/report/domain/Report.java`
- `backend/src/main/java/com/jobforge/backend/report/domain/ReportReason.java`
- `backend/src/main/java/com/jobforge/backend/report/domain/ReportStatus.java`
- `backend/src/main/java/com/jobforge/backend/report/domain/ReportTargetType.java`
- `backend/src/main/java/com/jobforge/backend/report/domain/ReportTransitions.java`
- `backend/src/main/java/com/jobforge/backend/report/infra/CompanyReportTarget.java`
- `backend/src/main/java/com/jobforge/backend/report/infra/JdbcReportRepository.java`
- `backend/src/main/java/com/jobforge/backend/report/infra/JobReportTarget.java`
- `backend/src/main/java/com/jobforge/backend/report/infra/UserReportTarget.java`
- `backend/src/main/java/com/jobforge/backend/user/facade/UserModerationFacade.java`
- `backend/src/test/java/com/jobforge/backend/report/ReportFlowIT.java`
- `backend/src/test/java/com/jobforge/backend/report/ReportTransitionsTest.java`
- `frontend/src/app/admin/reports/[id]/page.tsx`
- `frontend/src/app/admin/reports/page.tsx`
- `frontend/src/features/reports/AdminReportDetailView.tsx`
- `frontend/src/features/reports/AdminReportQueue.tsx`
- `frontend/src/features/reports/ReportButton.tsx`
- `frontend/src/features/reports/ReportForm.tsx`
- `frontend/src/features/reports/ReportStatusBadge.tsx`
- `frontend/src/features/reports/ResolvePanel.tsx`
- `frontend/src/features/reports/hooks.ts`
- `frontend/src/lib/api/reports.ts`
- `frontend/src/lib/reports/rules.ts`
- `frontend/src/lib/validation/reports.ts`
- `frontend/src/types/reports.ts`
- `frontend/tests/components/ReportForm.test.tsx`
- `frontend/tests/components/ResolvePanel.test.tsx`
- `frontend/tests/e2e/reports.spec.ts`
- `frontend/tests/unit/report-rules.test.ts`
- `frontend/tests/unit/report-schema.test.ts`

## MODIFIED files (13)
- `backend/src/main/java/com/jobforge/backend/company/app/CompanyAccessService.java`
- `backend/src/main/java/com/jobforge/backend/company/facade/CompanyAccessFacade.java`
- `backend/src/main/java/com/jobforge/backend/job/app/JobFacadeImpl.java`
- `backend/src/main/java/com/jobforge/backend/job/facade/JobFacade.java`
- `backend/src/main/java/com/jobforge/backend/job/facade/JobViews.java`
- `backend/src/main/java/com/jobforge/backend/notification/infra/NotificationKafkaConsumer.java`
- `backend/src/main/java/com/jobforge/backend/shared/audit/AuditAction.java`
- `backend/src/main/java/com/jobforge/backend/shared/events/EventTopics.java`
- `backend/src/main/java/com/jobforge/backend/user/app/UserDirectoryService.java`
- `frontend/src/app/(public)/companies/[id]/page.tsx`
- `frontend/src/app/(public)/jobs/[id]/page.tsx`
- `frontend/src/app/users/[id]/page.tsx`
- `frontend/src/components/layout/Header.tsx`

## Verify (not done here)
- `cd backend && mvn clean verify`
- `cd frontend && pnpm install && pnpm typecheck && pnpm lint && pnpm test && pnpm exec playwright test tests/e2e/reports.spec.ts`
