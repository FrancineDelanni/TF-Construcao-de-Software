-- The demonstration uses one active lot per event, with prices still attached to each seat.
CREATE TABLE inventory_lots (
 id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES inventory_events(id),
 name text NOT NULL, active boolean NOT NULL DEFAULT true,
 UNIQUE(event_id,id)
);
CREATE UNIQUE INDEX inventory_one_active_lot ON inventory_lots(event_id) WHERE active;
INSERT INTO inventory_lots(id,event_id,name,active)
 SELECT md5('pulsepass-lot-'||id::text)::uuid,id,'Lote Demonstração',true FROM inventory_events
 ON CONFLICT(id) DO NOTHING;
ALTER TABLE inventory_seats ADD COLUMN lot_id uuid;
UPDATE inventory_seats s SET lot_id=l.id FROM inventory_lots l WHERE s.event_id=l.event_id AND l.active;
ALTER TABLE inventory_seats ALTER COLUMN lot_id SET NOT NULL;
ALTER TABLE inventory_seats ADD CONSTRAINT inventory_seats_lot_fk FOREIGN KEY(event_id,lot_id) REFERENCES inventory_lots(event_id,id);
ALTER TABLE inventory_reservation_items ADD COLUMN lot_id uuid REFERENCES inventory_lots(id);
ALTER TABLE inventory_reservation_items ADD COLUMN lot_name text;
UPDATE inventory_reservation_items i SET lot_id=s.lot_id,lot_name=l.name
 FROM inventory_seats s JOIN inventory_lots l ON l.id=s.lot_id WHERE s.id=i.seat_id;
ALTER TABLE inventory_reservation_items ALTER COLUMN lot_id SET NOT NULL;
ALTER TABLE inventory_reservation_items ALTER COLUMN lot_name SET NOT NULL;
