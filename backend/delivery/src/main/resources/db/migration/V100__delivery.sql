CREATE TABLE ticket (
 id UUID PRIMARY KEY,order_item_id UUID NOT NULL UNIQUE,order_id UUID NOT NULL,
 customer_id TEXT NOT NULL,event_id UUID NOT NULL,event_name TEXT NOT NULL,
 event_starts_at TEXT NOT NULL,venue TEXT NOT NULL,seat_id UUID NOT NULL,
 seat_description TEXT NOT NULL,issued_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','ISSUED')),
 pdf BYTEA,attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 lease_until TIMESTAMPTZ,lease_token UUID,
 CHECK(status<>'ISSUED' OR pdf IS NOT NULL)
);
CREATE INDEX ticket_owner ON ticket(customer_id,order_id);
CREATE INDEX ticket_pending ON ticket(next_attempt_at) WHERE status<>'ISSUED';
