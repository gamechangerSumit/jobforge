-- DATABASE_SCHEMA §4.4 jobs + job_skills, with the §9 indexes. Requires core.companies (RC-1).
CREATE TABLE core.jobs (
    id                uuid PRIMARY KEY,
    company_id        uuid         NOT NULL REFERENCES core.companies (id),
    created_by        uuid         NOT NULL REFERENCES core.users (id),
    title             varchar(120) NOT NULL,
    slug              varchar(160) NOT NULL,
    description       text         NOT NULL,
    requirements      text,
    benefits          text,
    employment_type   varchar(15)  NOT NULL,
    work_mode         varchar(10)  NOT NULL,
    experience_level  varchar(12)  NOT NULL,
    location_city     varchar(80),
    location_state    varchar(80),
    location_country  char(2),
    salary_min        numeric(12, 0),
    salary_max        numeric(12, 0),
    salary_currency   char(3),
    salary_period     varchar(8),
    salary_visible    boolean      NOT NULL DEFAULT true,
    openings          smallint     NOT NULL DEFAULT 1,
    status            varchar(12)  NOT NULL DEFAULT 'DRAFT',
    published_at      timestamptz,
    expires_at        timestamptz,
    closed_at         timestamptz,
    ai_generated      boolean      NOT NULL DEFAULT false,
    ai_request_id     uuid,
    quality_score     smallint,
    removed_by        uuid REFERENCES core.users (id),
    removed_reason    varchar(500),
    search_vector     tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'C')) STORED,
    version           bigint       NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    CONSTRAINT uq_jobs_slug UNIQUE (slug),
    CONSTRAINT ck_jobs_employment_type CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'FREELANCE')),
    CONSTRAINT ck_jobs_work_mode CHECK (work_mode IN ('ONSITE', 'HYBRID', 'REMOTE')),
    CONSTRAINT ck_jobs_experience_level CHECK (experience_level IN ('INTERN', 'ENTRY', 'MID', 'SENIOR', 'LEAD', 'EXECUTIVE')),
    CONSTRAINT ck_jobs_salary_period CHECK (salary_period IN ('YEAR', 'MONTH', 'HOUR')),
    CONSTRAINT ck_jobs_salary_order CHECK (salary_max IS NULL OR salary_min IS NULL OR salary_max >= salary_min),
    CONSTRAINT ck_jobs_openings CHECK (openings BETWEEN 1 AND 1000),
    CONSTRAINT ck_jobs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED', 'CLOSED', 'EXPIRED', 'REMOVED')),
    CONSTRAINT ck_jobs_quality_score CHECK (quality_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_jobs_search_vector ON core.jobs USING gin (search_vector);
CREATE INDEX idx_jobs_published ON core.jobs (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX idx_jobs_company_status ON core.jobs (company_id, status);
CREATE INDEX idx_jobs_status_expires ON core.jobs (status, expires_at);
CREATE INDEX idx_jobs_title_trgm ON core.jobs USING gin (title public.gin_trgm_ops);
CREATE INDEX idx_jobs_published_filters ON core.jobs (work_mode, employment_type, experience_level)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX idx_jobs_published_location ON core.jobs (location_country, location_city)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX idx_jobs_published_salary ON core.jobs (salary_min, salary_max)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX idx_jobs_created_by ON core.jobs (created_by);
CREATE INDEX idx_jobs_removed_by ON core.jobs (removed_by);

CREATE TRIGGER trg_jobs_updated_at BEFORE UPDATE ON core.jobs
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE core.job_skills (
    job_id       uuid    NOT NULL REFERENCES core.jobs (id) ON DELETE CASCADE,
    skill_id     uuid    NOT NULL REFERENCES core.skills (id),
    is_required  boolean NOT NULL DEFAULT true,
    PRIMARY KEY (job_id, skill_id)
);

CREATE INDEX idx_job_skills_skill_job ON core.job_skills (skill_id, job_id);
