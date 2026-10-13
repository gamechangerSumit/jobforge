# REQ-20261010 — Session 4 Community: decisions required before Stage B
To: Dev 1 (backend), Dev 2 (frontend), Dev 3 (notification lane). Source: docs/session4/SESSION4_STAGE_A_REPORT.md §8.
Blocking: D1 (moderation matrix for POST/COMMENT incl. HIDE_CONTENT), D2 (audit), D3 (handle lookup facade), D4 (notification consumer lane), D5 (event payloads/notification rules), D9 (cursor signing secret), D12 (account-deletion hook), D13 (frontend in same session).
Non-blocking (defaults proposed in report): D6, D7, D8, D10, D11, D14, D15.
Required contract-change PR (CLAUDE.md §3, before code): API_CONTRACT §12.9.1 (POST/COMMENT supported), §12.11.1 (matrix rows), §12.9 (feed semantics, visibility, event/notification rules), ARCHITECTURE §14 (community payloads), CHANGELOG_CONTRACTS lines.
