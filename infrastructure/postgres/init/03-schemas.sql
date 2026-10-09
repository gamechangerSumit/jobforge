-- 03-schemas.sql
-- Schemas and default privileges per DATABASE_SCHEMA §2. Flyway (as jobforge_owner) creates the tables;
-- default privileges below give runtime roles DML on them automatically.
-- FK direction rules (community->core, platform->core, ai isolated) are enforced by migrations/review, not here.
\set ON_ERROR_STOP on

CREATE SCHEMA core      AUTHORIZATION jobforge_owner;
CREATE SCHEMA community AUTHORIZATION jobforge_owner;
CREATE SCHEMA platform  AUTHORIZATION jobforge_owner;
CREATE SCHEMA ai        AUTHORIZATION jobforge_owner;

GRANT USAGE ON SCHEMA core, community, platform TO jobforge_backend;
GRANT USAGE ON SCHEMA ai                        TO jobforge_ai;

ALTER DEFAULT PRIVILEGES FOR ROLE jobforge_owner IN SCHEMA core, community, platform
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jobforge_backend;
ALTER DEFAULT PRIVILEGES FOR ROLE jobforge_owner IN SCHEMA core, community, platform
  GRANT USAGE, SELECT ON SEQUENCES TO jobforge_backend;

ALTER DEFAULT PRIVILEGES FOR ROLE jobforge_owner IN SCHEMA ai
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jobforge_ai;
ALTER DEFAULT PRIVILEGES FOR ROLE jobforge_owner IN SCHEMA ai
  GRANT USAGE, SELECT ON SEQUENCES TO jobforge_ai;

-- NOTE: core.audit_logs must be INSERT/SELECT only for jobforge_backend. The table does not exist yet;
-- the REVOKE belongs in Dev 1's core migration (see docs/requests/REQ-20261001-db-bootstrap-and-kafka-listeners.md).
