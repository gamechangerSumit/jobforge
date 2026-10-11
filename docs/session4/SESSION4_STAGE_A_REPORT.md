> **Status update:** Stage A was followed by Stage B on the assumption that the proposals below were accepted (see `SESSION4_REPORT.md`, section "Assumptions"). Items D6, D12, D15 remain open.

# Session 4 — Community — Stage A (read-only planning) — COMPLETE, awaiting decisions
Base inspected: `jobforge-feature-session-3-reports-moderation.zip` (843 files; latest of the 3 uploaded; contains Sessions 0–3). No code changed. Nothing compiled/run/tested.
Read: docs/status/HANDOFF.md, NEXT_CLAUDE_PROMPT.md, CHANGELOG_CONTRACTS.md, DATABASE_SCHEMA §3.7/3.8/4.6/5/11–14, API_CONTRACT §6–10, §12.9, §12.9.1, §12.11.1, ARCHITECTURE (grep of community/events/security/rate), MASTER_SPEC (CM-1..4, AD-2, D-14), status/backend.md (Session 3A), CLAUDE.md, community migration, report module (ReportService, ReportTargetPort, UserReportTarget, ReportTargetType), EventTopics, AuditAction, CursorResponse/CursorMeta, NotificationKafkaConsumer (grep), ArchitectureTest (grep), MentionInput.tsx, frontend tree.
NOT read in detail (do in Stage B before coding): API_CONTRACT §4/§5.1/§11 (UserSummary), ARCHITECTURE §5/§14/§15 full text, JobReportTarget/CompanyReportTarget, interview module (as code-style sample), RequestRateLimitInterceptor path mapping, search package, frontend lib/api/client.ts, report UI, MSW mocks, UserDirectoryService/delete-account flow.

## 1. Verified architecture
Java 21/Spring Boot/Spring JDBC (`Db`/`JdbcClient`)/Flyway; module layout api/facade/app/domain/infra/events, cross-module only via facade, ArchUnit enforced (shared must not depend on community; domain pure). Envelope + typed exceptions → GlobalExceptionHandler; events only via `EventPublisher` (outbox), topics from `EventTopics`; audit via `AuditService` in same tx. `community/` currently only `package-info.java`. Frontend: Next.js App Router, TanStack Query, RHF+Zod, `lib/api/*`, MSW mocks, existing `MentionInput`, `features/reports/*` (ReportButton/ReportForm).

## 2. Roadmap check
Roadmap confirms Phase/Session 4 = Community (MASTER_SPEC CM-1..CM-4, AD-2 community; HANDOFF “POST/COMMENT report targets and HIDE_CONTENT go to the Community session”). No discrepancy.

## 3. Supported scope (contract-backed)
Endpoints (API_CONTRACT §12.9): GET/POST `/community/posts` (feed latest|following|trending, type, tag, authorId, q; cursor), GET/PATCH/DELETE `/community/posts/{id}`, PUT/DELETE `…/like`, `…/save`, POST `…/share`, GET `/community/saved`, GET/POST `/community/posts/{id}/comments`, PATCH/DELETE `/community/comments/{id}`, PUT/DELETE `/community/comments/{id}/like`, PUT/DELETE `/users/{id}/follow`, PUT/DELETE `/community/tags/{slug}/follow`, GET `/community/tags?q=` and `/community/tags/trending`. Post DTO, per-type `typeMetadata`, https-only URLs, tags ≤5 `[a-z0-9-]{2,30}`, body 1–10000, comment 1–2000, depth ≤2, `@handle` mentions parsed server-side, HTML stripped (`MarkdownSanitizer` exists), rate class `WRITE_COMMUNITY` 30/min.
Report integration: `PostReportTarget`, `CommentReportTarget` adapters (ReportTargetPort); filing for POST/COMMENT; admin resolve for them (see decisions D1).
Events/notifications (names only in ARCHITECTURE): PostCreated, CommentCreated, PostLiked, UserMentioned, UserFollowed on `jobforge.community.v1` → notification types POST_LIKED, POST_COMMENTED, COMMENT_REPLIED, USER_MENTIONED, USER_FOLLOWED (+ CONTENT_MODERATED already mapped).
Frontend: feed, create/edit/delete, detail+comments, saved, tag/author filter, follow buttons, share, ReportButton for POST/COMMENT, MentionInput wiring, admin report UI target labels for POST/COMMENT.
OUT of scope unless decided: company follow endpoints (table exists, no contract), analytics, AI, search internals.

## 4. Reuse (must not be rewritten)
`ReportService`, `ReportTargetPort`, `ReportTargetType`, `JdbcReportRepository`, admin report UI, `ReportButton`; `AuditService/AuditAction`, `EventPublisher/DomainEvent/EventTopics`, `NotificationKafkaConsumer`, `CursorResponse/CursorMeta`, `QueryParams`, `MarkdownSanitizer`, `CurrentUser/AuthenticatedUser`, `UserFacade` (+`UserSearchFacade`), `RateLimitClass.WRITE_COMMUNITY`, `MentionInput`, `lib/api/client.ts`.

## 5. Proposed files (Stage B)
Backend `com.jobforge.backend.community`: api (PostController, CommentController, FollowController, TagController + DTOs), app (PostService, CommentService, InteractionService, FollowService, TagService, MentionParser, FeedQuery, CursorCodec, CommunityEvents), domain (PostType, PostStatus, TypeMetadataValidator, rules), infra (JdbcPostRepository, JdbcCommentRepository, JdbcInteractionRepository, JdbcFollowRepository, JdbcTagRepository), facade (CommunityFacade for report/moderation/user-deletion hooks). `report/infra/PostReportTarget.java`, `CommentReportTarget.java`. Additive edits only: `EventTopics` (+COMMUNITY), `AuditAction` (only if approved), `NotificationKafkaConsumer`/consumer for community topic (lane note D4), `UserFacade` handle lookup (D3), `SecurityRulesContributor` if needed, report tests that currently assert 422 for POST/COMMENT.
Tests (NOT RUN): PostAuthorizationIT, CommentAuthorizationIT, InteractionIT, FeedPaginationIT, MentionParserTest, TypeMetadataValidatorTest, CursorCodecTest, ReportFlowIT additions.
Frontend: `app/community/**`, `features/community/*`, `lib/api/community.ts`, `types/community.ts`, `lib/validation/community.ts`, mocks, Vitest/Playwright tests; small edits to Header (nav) and report target labels.
Docs: contract-change text (API_CONTRACT §12.9/§12.9.1/§12.11.1, ARCHITECTURE §14 payloads, CHANGELOG_CONTRACTS), status files, SESSION4_README.

## 6. Migrations
`community/V202610070200__community_core.sql` ALREADY contains posts, tags, post_tags, comments, post_likes, comment_likes, post_saves, post_shares, user_follows, tag_follows, company_follows, mentions with all counters/indexes. **Expected: NO new migration.** Possible only if decisions require (e.g. spam body-hash column, D6) — would be a new timestamped file after V202610070200 plus DATABASE_SCHEMA change first (CLAUDE.md rule 12). Never edit applied migration.

## 7. Report/moderation/notification integration
- Adapters: snapshot(label = type + post title only, never body; reportable = ACTIVE & not soft-deleted & author ACTIVE; owner = author). Self-report → 422 (existing service logic). Missing/hidden → 404.
- Session-3 behavior to preserve: JOB/COMPANY/USER matrix; 422 for HIDE_CONTENT/REMOVE_CONTENT on those; ContentModerated payload has `ownerUserId` → already notifies. Existing tests asserting POST/COMMENT → 422 must be changed (intended change).
- ReportService.resolve calls `port.apply` for non-WARN actions; WARN_USER already generic via ownerUserId.

## 8. Contract gaps / decisions needed (do NOT invent)
D1 (blocker). Moderation matrix for POST/COMMENT is undefined; §12.11.1 says HIDE_CONTENT deferred to Community “until implemented and approved”. Proposal: DISMISS ✔, HIDE_CONTENT ✔ (status HIDDEN, reversible later), REMOVE_CONTENT ✔ (status REMOVED), WARN_USER ✔ (author), SUSPEND_USER ✗ (use USER report). Needs your approval + contract-change text.
D2. Audit: DATABASE_SCHEMA §13 has no POST_*/COMMENT_* actions. Proposal: author create/edit/delete not audited (same as interviews); admin delete (`DELETE …` by ADMIN) and moderation audited as `CONTENT_MODERATED`. Confirm.
D3. Mentions need handle→user lookup for all roles; `UserFacade` only has `handleTaken`; `UserSearchFacade` = seekers only. Need additive method in `user/facade` (touches completed module) — approve.
D4. Lane: notification consumer lives in Dev 3 lane (CLAUDE.md §2); Session 3 already edited it. Approve edit, or I write REQ for Dev 3 and stub.
D5. Event payload shapes for PostCreated/CommentCreated/PostLiked/UserMentioned/UserFollowed not specified; notification recipient/dedup rules (like toggling spam, self-like/self-comment/self-mention, reply goes to parent author) unspecified. Proposal in Stage B contract text.
D6. Anti-spam heuristics (ARCHITECTURE §288: link count, duplicate body hash, per-user post limits) have no numbers. Proposal: defer, rely on WRITE_COMMUNITY rate class only.
D7. `feed=following` (users+tags? companies?) and `trending` (formula/window) undefined; `GET /community/tags/trending` undefined. Proposal: following = authors followed ∪ posts with followed tags; trending = (likes+2·comments+saves) over last 7 days, tie id.
D8. Visibility of HIDDEN/REMOVED/deleted posts and comments (author/admin view? comments of removed post?). Proposal: non-visible → 404 for everyone except author and ADMIN (author sees status only on own).
D9. Cursor “opaque, signed, 24 h” — no codec exists; needs HMAC secret config (new env var → `.env.example` additive). Confirm.
D10. Share URL base (no env var seen) — need frontend base URL config name.
D11. Search `q`: ARCHITECTURE says D1 uses D3 SearchProvider helpers; not verified they exist. Fallback: direct `search_vector @@ websearch_to_tsquery` in community infra.
D12. Account deletion (`DELETE /users/me`) must clean likes/saves/follows (§11 hard delete) and anonymize authors — needs a hook (listener or facade) in the user module.
D13. Lane for UI: D-14 says Dev 2 owns Community UI; Session 3 did backend+frontend together. Confirm same here.
D14. Company follow: table only; no endpoint in contract → skip.
D15. Comment counter semantics (replies counted? decrement on delete? on HIDE?) and tombstone for deleted parent comments.

## 9. Status
Stage A done (documentation only). Stage B 0%. Do not start Stage B until D1–D5, D9, D12, D13 are answered.
