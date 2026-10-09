-- DATABASE_SCHEMA §4.2 education + experience (JS-2). Requires core.seeker_profiles (V202610021020).
CREATE TABLE core.education (
    id                uuid PRIMARY KEY,
    seeker_profile_id uuid         NOT NULL REFERENCES core.seeker_profiles (id) ON DELETE CASCADE,
    institution       varchar(150) NOT NULL,
    degree            varchar(100) NOT NULL,
    field_of_study    varchar(100),
    start_date        date         NOT NULL,
    end_date          date,
    grade             varchar(30),
    description       text,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_education_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_education_description CHECK (description IS NULL OR char_length(description) <= 1000)
);
CREATE INDEX idx_education_seeker_profile ON core.education (seeker_profile_id);
CREATE TRIGGER trg_education_updated_at BEFORE UPDATE ON core.education
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE core.experience (
    id                uuid PRIMARY KEY,
    seeker_profile_id uuid         NOT NULL REFERENCES core.seeker_profiles (id) ON DELETE CASCADE,
    title             varchar(120) NOT NULL,
    company_name      varchar(150) NOT NULL,
    location          varchar(120),
    employment_type   varchar(20),
    start_date        date         NOT NULL,
    end_date          date,
    is_current        boolean      NOT NULL DEFAULT false,
    description       text,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_experience_employment_type CHECK (employment_type IS NULL
        OR employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'FREELANCE')),
    CONSTRAINT ck_experience_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_experience_current CHECK (NOT (is_current AND end_date IS NOT NULL)),
    CONSTRAINT ck_experience_description CHECK (description IS NULL OR char_length(description) <= 2000)
);
CREATE INDEX idx_experience_seeker_profile ON core.experience (seeker_profile_id);
CREATE TRIGGER trg_experience_updated_at BEFORE UPDATE ON core.experience
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();
