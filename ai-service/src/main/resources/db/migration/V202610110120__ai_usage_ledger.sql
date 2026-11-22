-- Usage ledger is the quota source of truth; Redis is only an accelerator.
CREATE TABLE ai.ai_usage_ledger (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    request_id uuid NOT NULL REFERENCES ai.ai_requests (id),
    operation varchar(30) NOT NULL,
    credits smallint NOT NULL,
    state varchar(10) NOT NULL,
    period_day date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    settled_at timestamptz NULL,
    CONSTRAINT ck_ai_usage_ledger_credits CHECK (credits > 0),
    CONSTRAINT ck_ai_usage_ledger_state CHECK (state IN ('RESERVED','COMMITTED','RELEASED'))
);
CREATE INDEX idx_ai_usage_ledger_user_id_period_day_state ON ai.ai_usage_ledger (user_id, period_day, state);
CREATE INDEX idx_ai_usage_ledger_request_id ON ai.ai_usage_ledger (request_id);
