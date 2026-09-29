# System Constitution: Currency Converter SDD

## 1. Fundamental Principles & Invariants

### 1.1 Architectural Boundary & Responsibilities
- **Backend as Authoritative Source of Truth:** Business calculations, rate fetching, normalization, database persistence, and validation are strictly enforced on the Spring Boot backend. Client-side validation optimizes user experience but is never trusted as authoritative.
- **Spec-Driven Precedence:** Implementation code must strictly reflect approved specification artifacts (`constitution.md`, `spec.md`, `plan.md`, `tasks.md`). Changes to behavior require prior specification updates.

### 1.2 Mathematical & Financial Precision
- **Strict Prohibition of Floating-Point Types:** Floating-point representations (`float`, `double`, `Float`, `Double`) MUST NEVER be used for currency amounts, rates, nominals, or conversion results.
- **BigDecimal & Nominal Normalization:** All rate operations MUST account for currency `nominal` (e.g. 1, 10, 100 units).
  $$\text{unitRate}(\text{CUR}) = \frac{\text{CUR.rate}}{\text{CUR.nominal}}$$
  Intermediate cross-rate arithmetic must use `BigDecimal` with high-precision context (`MathContext.DECIMAL128`).
- **Rounding Boundary:** Rounding MUST NOT be performed during intermediate mathematical steps. Final presentation rounding is applied exclusively at the DTO / response boundary using `setScale(4, RoundingMode.HALF_UP)`.
- **Identity Conversion:** When source currency equals target currency, the converted amount MUST equal the original amount scaled to 4 decimal places without redundant network or division operations.

### 1.3 Data Integrity & Schema Safety
- **PostgreSQL Exclusivity:** The system targets PostgreSQL 16+. Emulated databases (e.g. H2 in compatibility mode) MUST NOT be used for schema verification or integration tests. Integration tests MUST use Testcontainers PostgreSQL.
- **Migration Authoritative:** Database schema is owned exclusively by Flyway migrations (`db/migration/V*`). Automatic DDL mutation (`ddl-auto=update` or `create-drop`) is strictly forbidden.
- **Idempotency & Concurrency:** Rates are uniquely constrained by `(currency_code, rate_date)`. Ingestion operations MUST be idempotent, leveraging explicit PostgreSQL `ON CONFLICT (currency_code, rate_date) DO UPDATE`.

### 1.4 Resilience & Two-Tier Offline Governance
- **Failure Mode A (Backend Offline from BNM):** If the external National Bank of Moldova (BNM) service is unreachable, timed out, or returns invalid bulletins, the backend MUST gracefully serve the latest persisted exchange rate from PostgreSQL, marking response metadata with `cached: true` and `offline: true`.
- **Failure Mode B (Frontend Offline from Backend):** If the client loses connection to the backend entirely, the frontend MUST NOT crash. It MUST fallback to the last-known-good snapshot stored in `localStorage` and display an prominent non-blocking offline banner.
- **Client Cache Isolation:** Pinia store MUST NOT be treated as the authoritative persistence/cache layer. Pinia manages ephemeral UI operational states (`idle`, `loading`, `success`, `error`, `offline`); persistent client snapshots are handled exclusively by a dedicated storage adapter (`SnapshotStorage`).

### 1.5 Security & External Integration Invariants
- **SSRF Immunity by Design:** External HTTP calls are architecturally restricted to an immutable configured BNM base endpoint (`https://www.bnm.md/en/official_exchange_rates`). User input MUST NOT control host, port, scheme, or URL path. Requested dates are strictly validated as `LocalDate`.
- **XXE Prevention:** XML parsers for external BNM bulletins MUST explicitly disable external entity resolution (`http://xml.org/sax/features/external-general-entities` = false, `http://xml.org/sax/features/external-parameter-entities` = false, `disallow-doctype-decl` = true).
- **REST Error Standards:** All API errors MUST adhere to RFC 9457 Problem Details (`application/problem+json`).
- **Security Headers & CORS:** Explicit CORS policy restricted to the frontend origin; standard security headers (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy`) enforced.
