-- 01-extensions.sql
-- Runs once, as the Postgres superuser, in $POSTGRES_DB on first start of an EMPTY data volume.
-- Contract: DATABASE_SCHEMA §2 (extensions: citext, pg_trgm, unaccent).
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
