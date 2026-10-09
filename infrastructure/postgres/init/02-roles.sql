-- 02-roles.sql
-- DB roles per DATABASE_SCHEMA §2. Passwords are read from the container environment
-- (JF_*_PASSWORD, mapped in docker-compose.yml) using psql \getenv (psql >= 15).
-- Role names are fixed by the contract and must match DB_MIGRATION_USER / DB_USER / AI_DB_USER in .env.
\set ON_ERROR_STOP on
\getenv owner_pw   JF_OWNER_PASSWORD
\getenv backend_pw JF_BACKEND_PASSWORD
\getenv ai_pw      JF_AI_PASSWORD

-- Runs Flyway migrations, owns all schema objects.
CREATE ROLE jobforge_owner   LOGIN PASSWORD :'owner_pw'   NOSUPERUSER NOCREATEDB NOCREATEROLE;
-- Backend runtime: DML on core/community/platform only (see 03-schemas.sql).
CREATE ROLE jobforge_backend LOGIN PASSWORD :'backend_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;
-- ai-service runtime: DML on schema ai only.
CREATE ROLE jobforge_ai      LOGIN PASSWORD :'ai_pw'      NOSUPERUSER NOCREATEDB NOCREATEROLE;

SELECT format('ALTER DATABASE %I OWNER TO jobforge_owner', current_database()) \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO jobforge_backend, jobforge_ai', current_database()) \gexec
