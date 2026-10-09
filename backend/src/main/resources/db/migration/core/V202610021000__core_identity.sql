-- DATABASE_SCHEMA §4.1 Identity (users, refresh_tokens, verification_tokens). Extensions live in `public`
-- (infrastructure/postgres/init/01-extensions.sql), so extension types/opclasses are schema-qualified.

CREATE FUNCTION core.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TABLE core.users (
    id                 uuid PRIMARY KEY,
    email              public.citext NOT NULL,
    password_hash      varchar(100)  NOT NULL,
    role               varchar(20)   NOT NULL,
    status             varchar(25)   NOT NULL DEFAULT 'PENDING_VERIFICATION',
    first_name         varchar(60)   NOT NULL,
    last_name          varchar(60)   NOT NULL,
    handle             public.citext NOT NULL,
    avatar_key         varchar(255),
    email_verified_at  timestamptz,
    last_login_at      timestamptz,
    token_version      int           NOT NULL DEFAULT 0,
    version            bigint        NOT NULL DEFAULT 0,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_handle UNIQUE (handle),
    CONSTRAINT ck_users_role CHECK (role IN ('JOB_SEEKER', 'RECRUITER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT ck_users_handle_format CHECK (handle ~ '^[a-z0-9_]{3,30}$')
);

CREATE INDEX idx_users_role_status ON core.users (role, status);
CREATE INDEX idx_users_name_trgm ON core.users USING gin ((first_name || ' ' || last_name) public.gin_trgm_ops);
CREATE INDEX idx_users_handle_trgm ON core.users USING gin ((handle::text) public.gin_trgm_ops);

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON core.users
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE core.refresh_tokens (
    id              uuid PRIMARY KEY,
    user_id         uuid        NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    family_id       uuid        NOT NULL,
    token_hash      char(64)    NOT NULL,
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz,
    replaced_by_id  uuid REFERENCES core.refresh_tokens (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    ip              inet,
    user_agent      varchar(255),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON core.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON core.refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON core.refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_replaced_by_id ON core.refresh_tokens (replaced_by_id);

CREATE TABLE core.verification_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    type        varchar(25) NOT NULL,
    token_hash  char(64)    NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_verification_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_verification_tokens_type CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ADMIN_INVITE'))
);

CREATE INDEX idx_verification_tokens_user_id ON core.verification_tokens (user_id);
