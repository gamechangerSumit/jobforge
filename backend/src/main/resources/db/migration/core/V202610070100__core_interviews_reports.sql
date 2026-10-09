-- DATABASE_SCHEMA 4.5 interviews, 4.6 reports + moderation_actions.
-- Requires core.users, core.applications (V202610051020). No forward references.

CREATE TABLE core.interviews (
    id                    uuid PRIMARY KEY,
    application_id        uuid          NOT NULL REFERENCES core.applications (id),
    scheduled_by          uuid          NOT NULL REFERENCES core.users (id),
    type                  varchar(10)   NOT NULL,
    scheduled_at          timestamptz   NOT NULL,
    duration_minutes      smallint      NOT NULL,
    timezone              varchar(50)   NOT NULL,
    location_or_link      varchar(500),
    status                varchar(12)   NOT NULL DEFAULT 'SCHEDULED',
    seeker_response       varchar(10)   NOT NULL DEFAULT 'PENDING',
    seeker_response_note  varchar(500),
    internal_notes        text,
    cancelled_reason      varchar(500),
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_interviews_type CHECK (type IN ('PHONE', 'VIDEO', 'ONSITE')),
    CONSTRAINT ck_interviews_status CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'DECLINED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_interviews_response CHECK (seeker_response IN ('PENDING', 'CONFIRMED', 'DECLINED')),
    CONSTRAINT ck_interviews_duration CHECK (duration_minutes BETWEEN 15 AND 480)
);
CREATE INDEX idx_interviews_application ON core.interviews (application_id, scheduled_at DESC);
CREATE INDEX idx_interviews_scheduled_by ON core.interviews (scheduled_by, scheduled_at DESC);
CREATE INDEX idx_interviews_upcoming ON core.interviews (scheduled_at) WHERE status IN ('SCHEDULED', 'CONFIRMED');
CREATE TRIGGER trg_interviews_updated_at BEFORE UPDATE ON core.interviews
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE core.reports (
    id           uuid PRIMARY KEY,
    reporter_id  uuid          NOT NULL REFERENCES core.users (id),
    target_type  varchar(10)   NOT NULL,
    target_id    uuid          NOT NULL,
    reason       varchar(20)   NOT NULL,
    details      varchar(1000),
    status       varchar(10)   NOT NULL DEFAULT 'OPEN',
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_reports_target_type CHECK (target_type IN ('POST', 'COMMENT', 'USER', 'JOB', 'COMPANY')),
    CONSTRAINT ck_reports_reason CHECK (reason IN ('SPAM', 'HARASSMENT', 'SCAM', 'INAPPROPRIATE', 'MISINFORMATION', 'DISCRIMINATION', 'OTHER')),
    CONSTRAINT ck_reports_status CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED'))
);
-- one active report per reporter and target
CREATE UNIQUE INDEX uq_reports_active ON core.reports (reporter_id, target_type, target_id)
    WHERE status IN ('OPEN', 'REVIEWING');
CREATE INDEX idx_reports_queue ON core.reports (status, created_at);
CREATE INDEX idx_reports_target ON core.reports (target_type, target_id);
CREATE TRIGGER trg_reports_updated_at BEFORE UPDATE ON core.reports
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE core.moderation_actions (
    id            uuid PRIMARY KEY,
    report_id     uuid          REFERENCES core.reports (id),
    moderator_id  uuid          NOT NULL REFERENCES core.users (id),
    action        varchar(15)   NOT NULL,
    target_type   varchar(10)   NOT NULL,
    target_id     uuid          NOT NULL,
    reason        varchar(500)  NOT NULL,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_moderation_action CHECK (action IN ('DISMISS', 'HIDE_CONTENT', 'REMOVE_CONTENT', 'WARN_USER', 'SUSPEND_USER')),
    CONSTRAINT ck_moderation_target_type CHECK (target_type IN ('POST', 'COMMENT', 'USER', 'JOB', 'COMPANY'))
);
CREATE INDEX idx_moderation_actions_report ON core.moderation_actions (report_id);
CREATE INDEX idx_moderation_actions_target ON core.moderation_actions (target_type, target_id);
