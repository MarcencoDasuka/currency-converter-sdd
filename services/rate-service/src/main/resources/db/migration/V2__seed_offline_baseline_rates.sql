INSERT INTO exchange_rates (numeric_code, currency_code, currency_name, nominal, rate, rate_date, fetched_at, external_id)
VALUES
    (840, 'USD', 'US Dollar', 1, 18.650000, DATE '2025-01-10', TIMESTAMPTZ '2025-01-10T08:00:00Z', '47'),
    (978, 'EUR', 'Euro', 1, 19.220000, DATE '2025-01-10', TIMESTAMPTZ '2025-01-10T08:00:00Z', '44'),
    (946, 'RON', 'Romanian Leu', 1, 3.865000, DATE '2025-01-10', TIMESTAMPTZ '2025-01-10T08:00:00Z', '35'),
    (980, 'UAH', 'Ukrainian Hryvnia', 1, 0.442000, DATE '2025-01-10', TIMESTAMPTZ '2025-01-10T08:00:00Z', '36'),
    (826, 'GBP', 'Pound Sterling', 1, 22.950000, DATE '2025-01-10', TIMESTAMPTZ '2025-01-10T08:00:00Z', '17'),
    (392, 'JPY', 'Japanese Yen', 100, 11.820000, DATE '2025-01-10', TIMESTAMPTZ '2025-01-10T08:00:00Z', '39')
ON CONFLICT (currency_code, rate_date) DO NOTHING;
