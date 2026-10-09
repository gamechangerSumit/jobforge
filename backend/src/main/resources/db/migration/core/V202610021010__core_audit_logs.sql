-- DATABASE_SCHEMA §4.6 / §13: append-only audit log. No FKs (rows must survive entity deletion).
-- REQ-20261001-db-bootstrap-and-kafka-listeners: default privileges would grant UPDATE/DELETE, so revoke explicitly.

CREATE TABLE core.audit_logs (
    id             uuid PRIMARY KEY,
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    actor_user_id  uuid,
    actor_role     varchar(20),
    source         varchar(20)  NOT NULL,
    action         varchar(60)  NOT NULL,
    entity_type    varchar(40)  NOT NULL,
    entity_id      uuid,
    outcome        varchar(10)  NOT NULL DEFAULT 'SUCCESS',
    before_state   jsonb,
    after_state    jsonb,
    metadata       jsonb,
    ip             inet,
    user_agent     varchar(255),
    request_id     uuid,
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_audit_logs_entity ON core.audit_logs (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_actor ON core.audit_logs (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_action ON core.audit_logs (action, occurred_at DESC);
CREATE INDEX idx_audit_logs_occurred_at_brin ON core.audit_logs USING brin (occurred_at);

CREATE FUNCTION core.audit_logs_immutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'core.audit_logs is append-only (% not allowed)', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON core.audit_logs
    FOR EACH ROW EXECUTE FUNCTION core.audit_logs_immutable();

REVOKE UPDATE, DELETE ON core.audit_logs FROM jobforge_backend;
