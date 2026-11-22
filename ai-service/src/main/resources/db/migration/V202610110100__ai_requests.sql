-- DATABASE_SCHEMA §7 / §9: ai.ai_requests (system of record for every AI request).
-- Schema ai is owned by ai-service; user_id is a plain UUID with no cross-schema FK.
CREATE TABLE ai.ai_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    user_role varchar(20) NOT NULL,
    operation varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    provider varchar(40) NOT NULL,
    model varchar(80) NOT NULL,
    input jsonb NOT NULL,
    input_hash char(64) NOT NULL,
    output jsonb NULL,
    output_schema_version varchar(30) NULL,
    validation_result jsonb NULL,
    quality_score smallint NULL,
    error_code varchar(40) NULL,
    error_message varchar(500) NULL,
    latency_ms int NULL,
    tokens_input int NULL,
    tokens_output int NULL,
    credits_charged smallint NOT NULL DEFAULT 0,
    idempotency_key uuid NULL,
    trace_id varchar(64) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz NULL,
    CONSTRAINT ck_ai_requests_operation CHECK (operation IN ('JOB_GENERATION','JOB_VALIDATION','JOB_RECOMMENDATION','CANDIDATE_RECOMMENDATION','CAREER_INSIGHT')),
    CONSTRAINT ck_ai_requests_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REJECTED_QUOTA','REJECTED_VALIDATION')),
    CONSTRAINT ck_ai_requests_quality_score CHECK (quality_score IS NULL OR quality_score BETWEEN 0 AND 100)
);
CREATE INDEX idx_ai_requests_user_id_created_at ON ai.ai_requests (user_id, created_at DESC, id DESC);
CREATE INDEX idx_ai_requests_operation_created_at ON ai.ai_requests (operation, created_at);
CREATE UNIQUE INDEX uq_ai_requests_user_id_idempotency_key ON ai.ai_requests (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
