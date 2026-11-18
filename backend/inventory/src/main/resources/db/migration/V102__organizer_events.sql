ALTER TABLE inventory_events ADD COLUMN organizer_id text;
ALTER TABLE inventory_events DROP CONSTRAINT inventory_events_status_check;
ALTER TABLE inventory_events ADD CONSTRAINT inventory_events_status_check
  CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED'));
CREATE INDEX inventory_events_organizer ON inventory_events(organizer_id);