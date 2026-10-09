# REQ-20261001 — DB bootstrap, audit_logs privileges, Kafka bootstrap addresses

**From:** Dev 3 (infra) **To:** Dev 1 (backend), FYI Dev 3 (ai-service) **Urgency:** before the first `core` migration (Phase 1)
**Status:** informational + one action for Dev 1. No contract text was changed.

## What infra now provides (Phase 0)
- Schemas `core`, `community`, `platform`, `ai` are **pre-created**, owned by `jobforge_owner` (`infrastructure/postgres/init/03-schemas.sql`).
- Roles per DATABASE_SCHEMA §2: `jobforge_owner` (migrations), `jobforge_backend`, `jobforge_ai`. Passwords from `DB_MIGRATION_PASSWORD`, `DB_PASSWORD`, `AI_DB_PASSWORD`.
- Default privileges: any table/sequence that `jobforge_owner` creates in `core|community|platform` is automatically `SELECT/INSERT/UPDATE/DELETE` for `jobforge_backend`; in `ai` for `jobforge_ai`.
- Extensions `citext`, `pg_trgm`, `unaccent` exist in `public`.

## Needed from Dev 1 (backend)
1. **Flyway config:** run as `DB_MIGRATION_USER`; one Flyway run per schema (`core`, then `community`, then `platform`) with `defaultSchema` = that schema, history table inside that schema, **`createSchemas=false`**. Locations per DATABASE_SCHEMA §2.
2. **`core.audit_logs` privileges (DATABASE_SCHEMA §4.6):** the table is created by your migration, so the migration must also run `REVOKE UPDATE, DELETE ON core.audit_logs FROM jobforge_backend;` (plus the immutability trigger). Default privileges would otherwise grant them.
3. Runtime datasource uses `DB_USER`/`DB_PASSWORD` (never the owner/superuser).

## Kafka bootstrap addresses
Apps on the host use `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`. Containers on `jobforge-net` (future `app` profile) must use `kafka:19092`. Topic auto-creation is **off**; topics are the 16 in ARCHITECTURE §14 (+`.dlq`). A new topic requires a contract change and an edit to `infrastructure/kafka/create-topics.sh`.

## Suggested follow-up (contract PR, optional)
Add `POSTGRES_*`, `KAFKA_CLUSTER_ID`, image-tag and host-port variables (already in `.env.example`) to ARCHITECTURE §27.
