CREATE TABLE IF NOT EXISTS conversions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    source_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    converted_amount NUMERIC(19, 4) NOT NULL,
    effective_rate NUMERIC(19, 6) NOT NULL,
    rate_date DATE NOT NULL,
    source_label VARCHAR(128) NOT NULL,
    offline BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversions_user_created
    ON conversions (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_events (created_at ASC)
    WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS local_rate_snapshots (
    currency_code VARCHAR(3) PRIMARY KEY,
    currency_name VARCHAR(128) NOT NULL,
    nominal INTEGER NOT NULL DEFAULT 1,
    rate NUMERIC(18, 6) NOT NULL,
    rate_date DATE NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
