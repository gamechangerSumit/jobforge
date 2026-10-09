-- DATABASE_SCHEMA §4.5 applications, application_status_history, application_notes. Requires core.resumes (JS-3).
CREATE TABLE core.applications (
    id                  uuid PRIMARY KEY,
    job_id              uuid        NOT NULL REFERENCES core.jobs (id),
    seeker_user_id      uuid        NOT NULL REFERENCES core.users (id),
    resume_id           uuid        NOT NULL REFERENCES core.resumes (id),
    cover_letter        text,
    status              varchar(15) NOT NULL DEFAULT 'SUBMITTED',
    profile_snapshot    jsonb       NOT NULL,
    rating              smallint,
    applied_at          timestamptz NOT NULL,
    status_updated_at   timestamptz NOT NULL,
    withdrawn_at        timestamptz,
    version             bigint      NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_applications_job_seeker UNIQUE (job_id, seeker_user_id), -- D-17
    CONSTRAINT ck_applications_status CHECK (status IN
        ('SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_applications_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_applications_cover_letter CHECK (char_length(cover_letter) <= 5000)
);

CREATE INDEX idx_applications_job_status ON core.applications (job_id, status);
CREATE INDEX idx_applications_seeker_applied ON core.applications (seeker_user_id, applied_at DESC);
CREATE INDEX idx_applications_status_updated ON core.applications (status, status_updated_at);
CREATE INDEX idx_applications_resume_id ON core.applications (resume_id);

CREATE TRIGGER trg_applications_updated_at BEFORE UPDATE ON core.applications
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE core.application_status_history (
    id              uuid PRIMARY KEY,
    application_id  uuid        NOT NULL REFERENCES core.applications (id) ON DELETE CASCADE,
    from_status     varchar(15),
    to_status       varchar(15) NOT NULL,
    changed_by      uuid        NOT NULL REFERENCES core.users (id),
    reason          varchar(500),
    created_at      timestamptz NOT NULL
);

CREATE INDEX idx_app_status_history_app_created ON core.application_status_history (application_id, created_at);
CREATE INDEX idx_app_status_history_changed_by ON core.application_status_history (changed_by);

CREATE TABLE core.application_notes (
    id               uuid PRIMARY KEY,
    application_id   uuid        NOT NULL REFERENCES core.applications (id),
    author_user_id   uuid        NOT NULL REFERENCES core.users (id),
    body             text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    CONSTRAINT ck_application_notes_body CHECK (char_length(body) <= 2000)
);

CREATE INDEX idx_application_notes_application ON core.application_notes (application_id);
CREATE INDEX idx_application_notes_author ON core.application_notes (author_user_id);

CREATE TRIGGER trg_application_notes_updated_at BEFORE UPDATE ON core.application_notes
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();
