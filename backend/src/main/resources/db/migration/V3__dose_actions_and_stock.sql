CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    medicine_id UUID NOT NULL REFERENCES medicines(id),
    pack_count INTEGER NOT NULL CHECK (pack_count > 0),
    units_per_pack NUMERIC(8,2) NOT NULL CHECK (units_per_pack > 0),
    total_units NUMERIC(8,2) NOT NULL CHECK (total_units > 0),
    price NUMERIC(12,2),
    pharmacy VARCHAR(200),
    purchased_on DATE NOT NULL,
    batch_number VARCHAR(100),
    expiry_date DATE,
    note VARCHAR(500),
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    voided_at TIMESTAMPTZ,
    voided_by UUID REFERENCES user_accounts(id)
);

CREATE TABLE stock_lots (
    id UUID PRIMARY KEY,
    medicine_id UUID NOT NULL REFERENCES medicines(id),
    purchase_id UUID REFERENCES purchases(id),
    original_units NUMERIC(8,2) NOT NULL CHECK (original_units > 0),
    remaining_units NUMERIC(8,2) NOT NULL CHECK (remaining_units >= 0),
    batch_number VARCHAR(100),
    expiry_date DATE,
    purchased_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_ledger_entries (
    id UUID PRIMARY KEY,
    medicine_id UUID NOT NULL REFERENCES medicines(id),
    stock_lot_id UUID REFERENCES stock_lots(id),
    dose_event_id UUID REFERENCES dose_events(id),
    purchase_id UUID REFERENCES purchases(id),
    delta NUMERIC(8,2) NOT NULL CHECK (delta <> 0),
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('PURCHASE','PURCHASE_VOID','DOSE_TAKEN','DOSE_UNDO','ADJUSTMENT','DISCARD','EXPIRED')),
    note VARCHAR(500),
    actor_id UUID REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_stock_dose_lot_reason ON stock_ledger_entries(dose_event_id, stock_lot_id, reason)
    WHERE dose_event_id IS NOT NULL AND stock_lot_id IS NOT NULL;

CREATE TABLE dose_action_requests (
    user_id UUID NOT NULL REFERENCES user_accounts(id),
    idempotency_key VARCHAR(100) NOT NULL,
    dose_event_id UUID NOT NULL REFERENCES dose_events(id),
    action VARCHAR(20) NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    result_acted_at TIMESTAMPTZ,
    result_acted_by UUID REFERENCES user_accounts(id),
    result_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    actor_id UUID REFERENCES user_accounts(id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    client_identifier VARCHAR(200),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reorder_requests (
    id UUID PRIMARY KEY,
    medicine_id UUID NOT NULL REFERENCES medicines(id),
    expected_by DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ORDERED','RECEIVED','CANCELLED')),
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ
);

ALTER TABLE households ADD COLUMN pharmacy_url_template VARCHAR(1000);

CREATE INDEX idx_stock_lots_fefo ON stock_lots(medicine_id, expiry_date, purchased_at) WHERE remaining_units > 0;
CREATE INDEX idx_stock_ledger_medicine ON stock_ledger_entries(medicine_id, created_at);
CREATE INDEX idx_doses_today ON dose_events(scheduled_at, medicine_id);
