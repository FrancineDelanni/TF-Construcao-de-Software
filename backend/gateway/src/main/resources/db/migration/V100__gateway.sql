CREATE TABLE gateway_attempt(attempt_id uuid PRIMARY KEY,outcome varchar(16) NOT NULL CHECK(outcome IN ('APPROVE','DECLINE','TIMEOUT')),created_at timestamptz NOT NULL DEFAULT clock_timestamp());
