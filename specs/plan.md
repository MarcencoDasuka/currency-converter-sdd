# Technical Implementation Plan: Currency Converter SDD

## 1. System Architecture

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 Vue 3 + Vite Frontend                  │
                  │  ┌────────────────────────┐  ┌──────────────────────┐  │
                  │  │ Pinia (UI State Only)  │  │ SnapshotStorage      │  │
                  │  │ - status, form errors  │  │ (localStorage cache) │  │
                  │  └────────────────────────┘  └──────────────────────┘  │
                  └──────────────────────────┬─────────────────────────────┘
                                             │ HTTP REST / JSON
                                             ▼
                  ┌────────────────────────────────────────────────────────┐
                  │             Spring Boot 3 Backend (Java 21)            │
                  │  ┌──────────────────────────────────────────────────┐  │
                  │  │ Controllers: ConversionController, CurrencyCtrl  │  │
                  │  └──────────────────────────┬───────────────────────┘  │
                  │  ┌──────────────────────────▼───────────────────────┐  │
                  │  │ Services:                                        │  │
                  │  │  - CurrencyConversionService (BigDecimal math)   │  │
                  │  │  - ExchangeRateService (Rollback logic)          │  │
                  │  │  - BnmClient + BnmXmlParser (Safe XML)           │  │
                  │  └──────────────┬────────────────────────┬──────────┘  │
                  └─────────────────┼────────────────────────┼─────────────┘
                                    │ SQL (JPA/Flyway)       │ HTTPS GET
                                    ▼                        ▼
                      ┌──────────────────────┐  ┌─────────────────────────┐
                      │    PostgreSQL 16+    │  │ National Bank of Moldova│
                      │  (Flyway migrations) │  │   (Official XML API)    │
                      └──────────────────────┘  └─────────────────────────┘
```

---

## 2. Directory Structure

```
currency-converter-sdd/
├── .spec/ (or specs/)
│   ├── constitution.md
│   ├── spec.md
│   ├── plan.md
│   └── tasks.md
├── backend/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── .mvn/wrapper/
│   └── src/
│       ├── main/
│       │   ├── java/com/converter/
│       │   │   ├── config/          # SecurityConfig, WebClientConfig, JacksonConfig
│       │   │   ├── controller/      # ConversionController, CurrencyController
│       │   │   ├── dto/             # ConversionRequest, ConversionResponse, ProblemDetail
│       │   │   ├── entity/          # ExchangeRateEntity
│       │   │   ├── exception/       # GlobalExceptionHandler, Custom Exceptions
│       │   │   ├── repository/      # ExchangeRateRepository
│       │   │   └── service/         # CurrencyConversionService, BnmClient, BnmXmlParser
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/    # V1__create_exchange_rates_table.sql
│       └── test/
│           ├── java/com/converter/  # Unit tests & Testcontainers integration tests
│           └── resources/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── components/              # CurrencyInput, CurrencySelect, ResultDisplay, Banner
│       ├── stores/                  # useCurrencyStore.ts (ephemeral UI state)
│       ├── storage/                 # SnapshotStorage.ts (localStorage persistence)
│       ├── api/                     # apiClient.ts
│       ├── types/                   # currency.ts
│       ├── App.vue
│       └── main.ts
├── docker-compose.yml               # Local PostgreSQL container definition
├── run-tests.bat                    # Single-command runner on Windows
├── run-tests.sh                     # Single-command runner on Unix
├── README.md
└── REPORT.md
```

---

## 3. Database Schema (PostgreSQL)

### Flyway Migration: `V1__create_exchange_rates_table.sql`
```sql
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
```

### PostgreSQL Idempotent Upsert Strategy
When persisting rates:
```sql
INSERT INTO exchange_rates (
    numeric_code, currency_code, currency_name, nominal, rate, rate_date, fetched_at, external_id
) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (currency_code, rate_date)
DO UPDATE SET
    rate = EXCLUDED.rate,
    nominal = EXCLUDED.nominal,
    currency_name = EXCLUDED.currency_name,
    fetched_at = EXCLUDED.fetched_at,
    external_id = EXCLUDED.external_id;
```

---

## 4. REST API Contracts

### 4.1 Get Currencies & Today's Rates
`GET /api/v1/currencies?date=2026-09-28`
Response: `200 OK`
```json
{
  "rateDate": "2026-09-26",
  "requestedDate": "2026-09-28",
  "source": "National Bank of Moldova",
  "cached": false,
  "offline": false,
  "rollbackDaysApplied": 2,
  "currencies": [
    {
      "code": "MDL",
      "name": "Moldovan Leu",
      "nominal": 1,
      "rate": 1.000000
    },
    {
      "code": "USD",
      "name": "US Dollar",
      "nominal": 1,
      "rate": 17.652100
    },
    {
      "code": "EUR",
      "name": "Euro",
      "nominal": 1,
      "rate": 19.341200
    }
  ]
}
```

### 4.2 Convert Currency
`POST /api/v1/convert`
Request Body:
```json
{
  "amount": 100.00,
  "sourceCurrency": "EUR",
  "targetCurrency": "USD",
  "date": "2026-09-28"
}
```
Response: `200 OK`
```json
{
  "amount": 100.00,
  "sourceCurrency": "EUR",
  "targetCurrency": "USD",
  "convertedAmount": 109.5690,
  "effectiveRate": 1.095690,
  "rateDate": "2026-09-26",
  "source": "National Bank of Moldova",
  "cached": false,
  "offline": false,
  "rollbackDaysApplied": 2
}
```

### 4.3 Validation Error Response (RFC 9457)
Response: `400 Bad Request`
```json
{
  "type": "https://api.currency-converter.local/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Input field validation errors occurred",
  "instance": "/api/v1/convert",
  "invalidParams": [
    {
      "name": "amount",
      "reason": "must be greater than 0"
    }
  ]
}
```

---

## 5. Security & Isolation Configurations
- **SSRF:** Base URL configured as `https://www.bnm.md/en/official_exchange_rates`.
- **CORS:** Allowed Origins: `http://localhost:5173`, `http://localhost:3000`. Wildcards prohibited.
- **Security Headers:** `Content-Security-Policy: default-src 'self'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`.
- **XML:** `DocumentBuilderFactory` configured with `setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)`.

---

## 6. Testing Strategy
- **Unit Tests:**
  - `BnmXmlParserTest`: Parser correctness on mock BNM XML, malformed XML, empty documents, XXE payload rejection.
  - `CurrencyConversionServiceTest`: Nominal calculation (1 vs 10 vs 100), cross-rates via MDL, identical currency, precision.
  - `ExchangeRateServiceRollbackTest`: Simulation of 404/500/empty responses triggering rollback up to 7 days.
  - `ValidationTest`: Input boundary checks.
- **Integration Tests:**
  - `PostgreSqlRateRepositoryIT`: Uses Testcontainers PostgreSQL to verify `ON CONFLICT DO UPDATE` idempotency and unique constraints.
