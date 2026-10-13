SESSION BOOTSTRAP
Lane: backend + frontend   Task: JobForge Session 4 (Community) — VERIFICATION and FIX PASS
Attached: the repo with the Session 4 overlay applied (or session4_community_overlay.zip on top of jobforge-feature-session-3-reports-moderation.zip), CLAUDE.md, docs/*, docs/session4/SESSION4_REPORT.md.
Rules: follow CLAUDE.md; no contract change without docs-first text; never claim a check passed unless it ran.
State: Session 4 is fully written but was NEVER compiled, built, linted, type-checked or tested.
Your job:
1. If you may run tools: `mvn -q verify` in backend (Docker for ITs) and `pnpm typecheck && pnpm lint && pnpm test` in frontend; fix compile/type/lint errors and failing tests with minimal changes inside Session 4 files.
2. If you may not run tools: re-read every Session 4 file (list in MANIFEST.txt) for compile errors, ArchUnit violations (domain purity, facade-only cross-module access, no web types in app), SQL mistakes and test/mock mismatches.
3. Confirm or reverse the assumptions in SESSION4_REPORT.md (moderation matrix, audit scope, Dev-3 lane edits, cursor key from JWT secret).
4. Review the fix pass (SESSION4_REPORT.md): UserDeleted purge, SpamGuard, exact comment recount with reply cascade, actor-name notifications, Playwright spec. Constructor changes to check in other tests/configs: `UserAccountService(.., EventPublisher)`, `NotificationKafkaConsumer(.., UserSummaryFacade)`, `PostService/CommentService(.., SpamGuard)`.
5. Remaining open items are listed under "Unresolved / follow-ups".
