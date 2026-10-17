CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE households (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    default_timezone VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE household_memberships (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'CAREGIVER', 'VIEWER')),
    UNIQUE (household_id, user_id)
);

CREATE TABLE household_invites (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CAREGIVER', 'VIEWER')),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE care_recipients (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    birth_year SMALLINT,
    timezone VARCHAR(80) NOT NULL,
    notes VARCHAR(1000),
    linked_user_id UUID REFERENCES user_accounts(id),
    archived BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE medicines (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES care_recipients(id),
    name VARCHAR(150) NOT NULL,
    strength VARCHAR(100),
    form VARCHAR(30) NOT NULL,
    units_per_dose NUMERIC(8,2) NOT NULL,
    instructions VARCHAR(1000),
    start_date DATE NOT NULL,
    end_date DATE,
    as_needed BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    lead_time_days INTEGER NOT NULL DEFAULT 3,
    CONSTRAINT medicine_quarter_units CHECK (MOD(units_per_dose, 0.25) = 0)
);

CREATE TABLE schedule_rule_versions (
    id UUID PRIMARY KEY,
    medicine_id UUID NOT NULL REFERENCES medicines(id),
    rule_type VARCHAR(30) NOT NULL CHECK (rule_type IN ('DAILY', 'WEEKDAYS', 'EVERY_N_DAYS')),
    interval_days INTEGER,
    anchor_date DATE,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    CONSTRAINT schedule_effective_range CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE schedule_rule_times (
    schedule_rule_id UUID NOT NULL REFERENCES schedule_rule_versions(id) ON DELETE CASCADE,
    local_time TIME NOT NULL,
    PRIMARY KEY (schedule_rule_id, local_time)
);

CREATE TABLE schedule_rule_days (
    schedule_rule_id UUID NOT NULL REFERENCES schedule_rule_versions(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    PRIMARY KEY (schedule_rule_id, day_of_week)
);

CREATE TABLE dose_events (
    id UUID PRIMARY KEY,
    medicine_id UUID NOT NULL REFERENCES medicines(id),
    schedule_rule_id UUID REFERENCES schedule_rule_versions(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    scheduled_local TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    acted_at TIMESTAMPTZ,
    acted_by UUID REFERENCES user_accounts(id),
    note VARCHAR(500),
    UNIQUE (medicine_id, scheduled_local)
);

CREATE INDEX idx_membership_user ON household_memberships(user_id, household_id);
CREATE INDEX idx_recipient_household ON care_recipients(household_id);
CREATE INDEX idx_medicine_recipient ON medicines(recipient_id);
CREATE INDEX idx_dose_pending ON dose_events(scheduled_at) WHERE status = 'PENDING';
