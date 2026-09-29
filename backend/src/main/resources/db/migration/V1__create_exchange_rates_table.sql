CREATE TABLE IF NOT EXISTS exchange_rates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numeric_code INTEGER NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    currency_name VARCHAR(128) NOT NULL,
    nominal INTEGER NOT NULL DEFAULT 1,
    rate NUMERIC(18, 6) NOT NULL,
    rate_date DATE NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    external_id VARCHAR(64),
    CONSTRAINT uq_exchange_rate_currency_date UNIQUE (currency_code, rate_date)
);

CREATE INDEX IF NOT EXISTS idx_exchange_rates_lookup
    ON exchange_rates (currency_code, rate_date DESC);
