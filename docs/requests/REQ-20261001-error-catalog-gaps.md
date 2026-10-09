# REQ-20261001 — Error catalog gaps, unknown-field code, health endpoints, dependency/EOL notes

**From:** Dev 1 (backend) **To:** Dev 2, Dev 3 (all contract owners) **Urgency:** before Phase 1 contract PR
**Status:** proposal. No contract text was changed; interim behaviour is implemented as described.

## 1. Missing HTTP statuses in API_CONTRACT §5.1
Spring MVC can raise 405 (method not allowed) and 406 (not acceptable); the catalog has no code for them.
- **Interim:** 405 → `404 RESOURCE_NOT_FOUND`; 406 → `400 MALFORMED_REQUEST`.
- **Proposed (§5.1):** add `405 METHOD_NOT_ALLOWED` and `406 NOT_ACCEPTABLE`. Needs Dev 2 review (error mapping in `lib/api`).

## 2. Unknown request field: two different codes in the contract
§1 says unknown fields → `400 VALIDATION_FAILED`; §5.1 lists "unknown field" under `MALFORMED_REQUEST`; §5.1 also defines detail code `UNKNOWN_FIELD`.
- **Interim:** `400 VALIDATION_FAILED` with `details[{field, code: "UNKNOWN_FIELD"}]`.
- **Proposed (§5.1):** remove "unknown field" from the `MALFORMED_REQUEST` row.

## 3. Health endpoints are not in API_CONTRACT
Phase 0 uses Spring Actuator, outside `/api/v1`, not wrapped in the envelope: `/actuator/health/liveness`, `/actuator/health/readiness` (readiness = readinessState + db; Redis/Kafka outages degrade, they do not make the app unready — ARCHITECTURE §14/§15). **Proposed:** mention in ARCHITECTURE §21/§28 (docs-only).

## 4. Dev 3 (infra / platform)
- `backend/pom.xml` already contains `spring-boot-starter-data-redis` and `spring-kafka` (needed so Redis/Kafka config binds). No beans, templates or listeners exist; Dev 3 owns `platform/**` and may adjust these in its dependency PR (ARCHITECTURE §25.3).
- `db/migration/platform` is Dev 3's. The backend runs Flyway for `platform` with `failOnMissingLocations=false`, so it is a no-op until files exist.
- Please confirm env var names in `.env.example` match ARCHITECTURE §27 (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`). `.env.example` and `docker-compose.yml` were not available to Dev 1 in this session.

## 5. Spring Boot 3.5 reached end of OSS support
3.5.16 (June 2026) is the last OSS 3.x release. Pinned to it per MASTER_SPEC §4 ("Boot 3.x"). **Proposed:** ADR for a Boot 4.x migration before Phase 3.

## 6. Phase 1 reminder (core migration)
The first `core` migration that creates `audit_logs` must include `REVOKE UPDATE, DELETE ON core.audit_logs FROM jobforge_backend;` and the `trg_audit_immutable` trigger (REQ-20261001-db-bootstrap, DATABASE_SCHEMA §4.6).
