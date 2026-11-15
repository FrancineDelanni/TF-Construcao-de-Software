CREATE TABLE outbox (
 seq BIGSERIAL PRIMARY KEY, event_id UUID NOT NULL UNIQUE, aggregate_id UUID NOT NULL,
 aggregate_version BIGINT NOT NULL, topic TEXT NOT NULL, payload TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(), sent_at TIMESTAMPTZ,
 attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX outbox_pending ON outbox(seq) WHERE sent_at IS NULL;
CREATE TABLE inbox (consumer TEXT NOT NULL,event_id UUID NOT NULL,processed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(consumer,event_id));
CREATE TABLE consumer_quarantine (
 consumer TEXT NOT NULL,event_id UUID NOT NULL,aggregate_id TEXT NOT NULL,topic TEXT NOT NULL,
 partition_id INT NOT NULL,offset_id BIGINT NOT NULL,payload TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),resolved_at TIMESTAMPTZ,
 PRIMARY KEY(consumer,event_id)
);
CREATE INDEX consumer_blocked ON consumer_quarantine(consumer,aggregate_id) WHERE resolved_at IS NULL;
