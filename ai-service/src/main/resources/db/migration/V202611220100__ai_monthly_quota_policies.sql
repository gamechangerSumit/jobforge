-- Monthly quota guardrail. Defaults are 30x the documented daily allowance; admins may tune either period independently.
INSERT INTO ai.ai_quota_policies (role, period, credit_limit, rate_limit_per_minute) VALUES
    ('RECRUITER','MONTHLY',3000,10),
    ('JOB_SEEKER','MONTHLY',600,10),
    ('ADMIN','MONTHLY',15000,20)
ON CONFLICT (role, period) DO NOTHING;
