CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    discreet_mode BOOLEAN NOT NULL DEFAULT FALSE,
    dose_missed_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    timezone VARCHAR(80) NOT NULL DEFAULT 'UTC'
);

CREATE TABLE telegram_accounts (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id) ON DELETE CASCADE,
    chat_id VARCHAR(100) UNIQUE,
    link_code_hash VARCHAR(64),
    link_code_expires_at TIMESTAMPTZ,
    linked_at TIMESTAMPTZ
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    household_id UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    dose_event_id UUID REFERENCES dose_events(id) ON DELETE CASCADE,
    medicine_id UUID REFERENCES medicines(id),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL','TELEGRAM')),
    notification_type VARCHAR(40) NOT NULL CHECK (notification_type IN ('DOSE_DUE','DOSE_FOLLOWUP','DOSE_MISSED','LOW_STOCK','OUT_OF_STOCK_CRITICAL','EXPIRY','DAILY_SUMMARY','TEST')),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('HIGH','NORMAL')),
    subject VARCHAR(200) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    deep_link VARCHAR(1000),
    dedupe_key VARCHAR(500) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    claimed_by VARCHAR(100),
    sent_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_due ON notifications(next_attempt_at, priority, created_at)
    WHERE status IN ('PENDING','FAILED','PROCESSING');
CREATE INDEX idx_notifications_dose ON notifications(dose_event_id, notification_type);
