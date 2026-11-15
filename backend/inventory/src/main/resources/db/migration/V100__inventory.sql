CREATE TABLE inventory_events (
 id uuid PRIMARY KEY, name text NOT NULL, starts_at timestamptz NOT NULL, venue text NOT NULL,
 status text NOT NULL CHECK (status IN ('PUBLISHED','CANCELLED')), currency varchar(3) NOT NULL DEFAULT 'BRL'
);
CREATE TABLE inventory_reservations (
 id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES inventory_events(id), customer_id text NOT NULL,
 status text NOT NULL CHECK (status IN ('ACTIVE','CONFIRMED','EXPIRED','CANCELLED')),
 expires_at timestamptz NOT NULL, order_id uuid UNIQUE, version bigint NOT NULL CHECK (version > 0),
 event_name text NOT NULL, event_starts_at timestamptz NOT NULL, venue text NOT NULL, currency varchar(3) NOT NULL,
 total_cents bigint NOT NULL CHECK (total_cents >= 0), created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX inventory_reservations_expiry ON inventory_reservations(status,expires_at);
CREATE TABLE inventory_seats (
 id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES inventory_events(id), sector text NOT NULL,
 seat_row text NOT NULL, seat_number text NOT NULL, price_cents bigint NOT NULL CHECK (price_cents >= 0),
 status text NOT NULL CHECK(status IN ('AVAILABLE','RESERVED','SOLD')), reservation_id uuid REFERENCES inventory_reservations(id),
 UNIQUE(event_id,sector,seat_row,seat_number),
 CHECK ((status='AVAILABLE' AND reservation_id IS NULL) OR (status IN ('RESERVED','SOLD') AND reservation_id IS NOT NULL))
);
CREATE INDEX inventory_seats_occupancy ON inventory_seats(reservation_id);
CREATE TABLE inventory_reservation_items (
 id uuid PRIMARY KEY, reservation_id uuid NOT NULL REFERENCES inventory_reservations(id),
 seat_id uuid NOT NULL REFERENCES inventory_seats(id), sector text NOT NULL, seat_row text NOT NULL,
 seat_number text NOT NULL, price_cents bigint NOT NULL CHECK(price_cents>=0), UNIQUE(reservation_id,seat_id)
);
CREATE TABLE inventory_idempotency (
 customer_id text NOT NULL, route text NOT NULL, idempotency_key text NOT NULL, payload_hash text NOT NULL,
 response jsonb, created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(customer_id,route,idempotency_key)
);
INSERT INTO inventory_events(id,name,starts_at,venue,status) VALUES
 ('10000000-0000-0000-0000-000000000001','Festival Pulse – Demonstração','2027-11-28T22:00:00Z','Arena de Demonstração','PUBLISHED'),
 ('10000000-0000-0000-0000-000000000002','Noite de Jazz','2027-12-05T23:00:00Z','Teatro Pulse','PUBLISHED');
INSERT INTO inventory_seats(id,event_id,sector,seat_row,seat_number,price_cents,status)
 SELECT md5('pulsepass-seat-'||e.id::text||'-'||n)::uuid,e.id,
 CASE WHEN n<=12 THEN 'Plateia' ELSE 'Balcão' END,
 CASE WHEN n<=12 THEN 'A' ELSE 'B' END,(((n-1)%12)+1)::text,
 CASE WHEN n<=12 THEN 15000 ELSE 9000 END,'AVAILABLE'
 FROM inventory_events e CROSS JOIN generate_series(1,24) n;
