-- DATABASE_SCHEMA §4.4 saved_jobs (hard-deleted on unsave, §11).
CREATE TABLE core.saved_jobs (
    seeker_user_id  uuid        NOT NULL REFERENCES core.users (id),
    job_id          uuid        NOT NULL REFERENCES core.jobs (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (seeker_user_id, job_id)
);

CREATE INDEX idx_saved_jobs_seeker_created ON core.saved_jobs (seeker_user_id, created_at DESC);
CREATE INDEX idx_saved_jobs_job_id ON core.saved_jobs (job_id);
