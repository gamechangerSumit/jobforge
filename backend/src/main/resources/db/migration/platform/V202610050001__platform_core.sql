CREATE TABLE IF NOT EXISTS platform.outbox_events
(
    id              uuid PRIMARY KEY,
    aggregate_type  varchar(40) NOT NULL,
    aggregate_id    uuid NOT NULL,
    event_type      varchar(60) NOT NULL,
    event_version   smallint NOT NULL DEFAULT 1,
    topic           varchar(80) NOT NULL,
    payload         jsonb NOT NULL,
    headers         jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    published_at    timestamptz NULL,
    attempts        int NOT NULL DEFAULT 0,
    last_error      varchar(500)
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON platform.outbox_events(created_at)
    WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS platform.processed_events
(
    consumer_group varchar(80) NOT NULL,
    event_id       uuid NOT NULL,
    processed_at   timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (consumer_group, event_id)
);

CREATE TABLE IF NOT EXISTS platform.notifications
(
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL,
    type       varchar(40) NOT NULL,
    title      varchar(150) NOT NULL,
    body       varchar(500) NOT NULL,
    data       jsonb NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key varchar(120),
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_dedupe
    ON platform.notifications(user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON platform.notifications(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_unread
    ON platform.notifications(user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS platform.notification_preferences
(
    user_id         uuid NOT NULL,
    type            varchar(40) NOT NULL,
    in_app_enabled  boolean NOT NULL DEFAULT true,
    email_enabled   boolean NOT NULL DEFAULT true,

    PRIMARY KEY (user_id, type)
);

CREATE TABLE IF NOT EXISTS platform.email_outbox
(
    id             uuid PRIMARY KEY,
    user_id        uuid,
    to_email       varchar(254) NOT NULL,
    template       varchar(60) NOT NULL,
    payload        jsonb NOT NULL,
    status         varchar(10) NOT NULL DEFAULT 'PENDING',
    attempts       smallint NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    sent_at        timestamptz,
    last_error     varchar(500),
    created_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_email_outbox_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_email_outbox_pending
    ON platform.email_outbox(next_attempt_at)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS platform.shedlock
(
    name       varchar(64) PRIMARY KEY,
    lock_until timestamptz NOT NULL,
    locked_at  timestamptz NOT NULL,
    locked_by  varchar(255) NOT NULL
);