-- Role quota policies and per-user overrides. Daily defaults follow API_CONTRACT §14.6.
CREATE TABLE ai.ai_quota_policies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role varchar(20) NOT NULL,
    period varchar(10) NOT NULL,
    credit_limit int NOT NULL,
    rate_limit_per_minute int NOT NULL,
    active boolean NOT NULL DEFAULT true,
    updated_by uuid NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_quota_policies_period CHECK (period IN ('DAILY','MONTHLY')),
    CONSTRAINT ck_ai_quota_policies_credit_limit CHECK (credit_limit >= 0),
    CONSTRAINT ck_ai_quota_policies_rate_limit CHECK (rate_limit_per_minute > 0),
    CONSTRAINT uq_ai_quota_policies_role_period UNIQUE (role, period)
);
CREATE TABLE ai.ai_quota_overrides (
    user_id uuid NOT NULL,
    period varchar(10) NOT NULL,
    credit_limit int NOT NULL,
    expires_at timestamptz NULL,
    reason varchar(500) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_quota_overrides PRIMARY KEY (user_id, period)
);
INSERT INTO ai.ai_quota_policies (role, period, credit_limit, rate_limit_per_minute) VALUES
    ('RECRUITER','DAILY',100,10),
    ('JOB_SEEKER','DAILY',20,10),
    ('ADMIN','DAILY',500,20);
