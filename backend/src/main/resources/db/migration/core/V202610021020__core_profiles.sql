-- DATABASE_SCHEMA §4.2 (seeker_profiles, skills, seeker_skills) and §4.3 (recruiter_profiles).
-- education/experience/resumes and companies/company_members follow in later slices.

CREATE TABLE core.seeker_profiles (
    id                        uuid PRIMARY KEY,
    user_id                   uuid        NOT NULL REFERENCES core.users (id),
    headline                  varchar(120),
    summary                   text,
    phone                     varchar(20),
    location_city             varchar(80),
    location_state            varchar(80),
    location_country          char(2),
    current_title             varchar(120),
    years_experience          numeric(3, 1),
    expected_salary_min       numeric(12, 0),
    expected_salary_max       numeric(12, 0),
    expected_salary_currency  char(3),
    expected_salary_period    varchar(8),
    notice_period_days        smallint,
    open_to_work              boolean     NOT NULL DEFAULT true,
    visibility                varchar(20) NOT NULL DEFAULT 'RECRUITERS_ONLY',
    linkedin_url              varchar(255),
    github_url                varchar(255),
    portfolio_url             varchar(255),
    completeness_score        smallint    NOT NULL DEFAULT 0,
    version                   bigint      NOT NULL DEFAULT 0,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_seeker_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_seeker_profiles_summary_len CHECK (char_length(summary) <= 2000),
    CONSTRAINT ck_seeker_profiles_years CHECK (years_experience >= 0 AND years_experience <= 60),
    CONSTRAINT ck_seeker_profiles_salary_order CHECK (expected_salary_max >= expected_salary_min),
    CONSTRAINT ck_seeker_profiles_salary_period CHECK (expected_salary_period IN ('YEAR', 'MONTH', 'HOUR')),
    CONSTRAINT ck_seeker_profiles_notice CHECK (notice_period_days >= 0 AND notice_period_days <= 365),
    CONSTRAINT ck_seeker_profiles_visibility CHECK (visibility IN ('PUBLIC', 'RECRUITERS_ONLY', 'PRIVATE')),
    CONSTRAINT ck_seeker_profiles_linkedin_https CHECK (linkedin_url ~ '^https://'),
    CONSTRAINT ck_seeker_profiles_github_https CHECK (github_url ~ '^https://'),
    CONSTRAINT ck_seeker_profiles_portfolio_https CHECK (portfolio_url ~ '^https://'),
    CONSTRAINT ck_seeker_profiles_score CHECK (completeness_score >= 0 AND completeness_score <= 100)
);

CREATE INDEX idx_seeker_profiles_location ON core.seeker_profiles (location_country, location_city);

CREATE TRIGGER trg_seeker_profiles_updated_at BEFORE UPDATE ON core.seeker_profiles
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE core.skills (
    id           uuid PRIMARY KEY,
    name         public.citext NOT NULL,
    slug         varchar(60)   NOT NULL,
    category     varchar(40),
    is_verified  boolean       NOT NULL DEFAULT false,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_skills_name UNIQUE (name),
    CONSTRAINT uq_skills_slug UNIQUE (slug)
);

CREATE INDEX idx_skills_name_trgm ON core.skills USING gin ((name::text) public.gin_trgm_ops);

CREATE TABLE core.seeker_skills (
    seeker_profile_id  uuid        NOT NULL REFERENCES core.seeker_profiles (id) ON DELETE CASCADE,
    skill_id           uuid        NOT NULL REFERENCES core.skills (id),
    proficiency        varchar(15) NOT NULL,
    years_experience   numeric(3, 1),
    PRIMARY KEY (seeker_profile_id, skill_id),
    CONSTRAINT ck_seeker_skills_proficiency CHECK (proficiency IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
);

CREATE INDEX idx_seeker_skills_skill_id ON core.seeker_skills (skill_id);

CREATE TABLE core.recruiter_profiles (
    id                uuid PRIMARY KEY,
    user_id           uuid        NOT NULL REFERENCES core.users (id),
    job_title         varchar(100),
    phone             varchar(20),
    approval_status   varchar(15) NOT NULL DEFAULT 'PENDING',
    approved_by       uuid REFERENCES core.users (id),
    approved_at       timestamptz,
    rejection_reason  varchar(500),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_recruiter_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_recruiter_profiles_approval CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

CREATE INDEX idx_recruiter_profiles_approved_by ON core.recruiter_profiles (approved_by);

CREATE TRIGGER trg_recruiter_profiles_updated_at BEFORE UPDATE ON core.recruiter_profiles
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();
