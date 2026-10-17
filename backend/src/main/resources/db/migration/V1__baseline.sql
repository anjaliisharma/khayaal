CREATE TABLE schema_marker (
    id SMALLINT PRIMARY KEY,
    description VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_marker (id, description) VALUES (1, 'Phase 0 baseline');

