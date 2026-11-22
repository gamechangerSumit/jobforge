-- Retention policy: redact input after 90 days while retaining request metadata.
ALTER TABLE ai.ai_requests ALTER COLUMN input DROP NOT NULL;
