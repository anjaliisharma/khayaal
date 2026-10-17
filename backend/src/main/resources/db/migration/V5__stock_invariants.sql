ALTER TABLE purchases
    ADD CONSTRAINT purchase_whole_units_per_pack
    CHECK (MOD(units_per_pack, 1) = 0) NOT VALID;

CREATE TABLE stock_shortages (
    id UUID PRIMARY KEY,
    medicine_id UUID NOT NULL REFERENCES medicines(id),
    dose_event_id UUID NOT NULL REFERENCES dose_events(id) ON DELETE CASCADE,
    unmet_units NUMERIC(8,2) NOT NULL CHECK (unmet_units > 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE (dose_event_id)
);

CREATE INDEX idx_stock_shortages_medicine ON stock_shortages(medicine_id, recorded_at);
