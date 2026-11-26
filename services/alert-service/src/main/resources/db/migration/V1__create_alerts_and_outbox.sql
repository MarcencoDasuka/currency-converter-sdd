CREATE TABLE IF NOT EXISTS rate_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('ABOVE', 'BELOW')),
    target_rate NUMERIC(18, 6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    triggered_rate NUMERIC(18, 6),
    triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rate_alerts_user_active
    ON rate_alerts (user_id, active);

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

CREATE INDEX IF NOT EXISTS idx_alert_outbox_pending
    ON outbox_events (created_at ASC)
    WHERE published_at IS NULL;
