CREATE TABLE customer_order (
 id uuid PRIMARY KEY, reservation_id uuid NOT NULL, customer_id varchar(255) NOT NULL,
 state varchar(32) NOT NULL, snapshot jsonb NOT NULL DEFAULT '{}', version bigint NOT NULL DEFAULT 1,
 inventory_version bigint NOT NULL DEFAULT -1, payment_status varchar(32) NOT NULL DEFAULT 'NONE',
 cancel_requested boolean NOT NULL DEFAULT false, rejected boolean NOT NULL DEFAULT false, blocked boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX one_order_per_reservation ON customer_order(reservation_id) WHERE rejected=false;
CREATE INDEX customer_order_owner ON customer_order(customer_id,created_at);
CREATE TABLE order_history (id bigserial PRIMARY KEY,order_id uuid NOT NULL REFERENCES customer_order(id),status varchar(32) NOT NULL,at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TABLE order_idempotency (customer_id varchar(255) NOT NULL,operation varchar(100) NOT NULL,key varchar(200) NOT NULL,request_hash varchar(64) NOT NULL,order_id uuid NOT NULL REFERENCES customer_order(id),PRIMARY KEY(customer_id,operation,key));
CREATE TABLE payment_attempt(id uuid PRIMARY KEY,order_id uuid NOT NULL REFERENCES customer_order(id),outcome varchar(16) NOT NULL,status varchar(32) NOT NULL DEFAULT 'PENDING',created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE UNIQUE INDEX one_pending_attempt ON payment_attempt(order_id) WHERE status='PENDING';
CREATE TABLE reservation_observation(reservation_id uuid PRIMARY KEY,version bigint NOT NULL,payload jsonb NOT NULL,received_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TABLE orders_quarantine(id uuid PRIMARY KEY,aggregate_id uuid,original text NOT NULL,error varchar(300) NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),replayed_at timestamptz);
