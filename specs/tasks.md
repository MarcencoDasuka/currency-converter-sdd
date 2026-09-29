# Implementation Tasks: Currency Converter SDD

## Task Sequence Overview
```
Phase 1: Specifications (Branch: specifications)
      ↓
Phase 2: Backend Implementation (Branch: feature/backend)
      ↓
Phase 3: Frontend Implementation (Branch: feature/frontend)
      ↓
Phase 4: Testing & Verification (Branch: feature/tests)
      ↓
Phase 5: Release & Reporting (Branch: main)
```

---

## Phase 1: Specifications (Branch: `specifications`)
- [x] **TASK-101**: Initialize Spec Kit structure in `specs/`.
- [x] **TASK-102**: Author `specs/constitution.md` (invariants, math precision, security rules, offline tiers).
- [x] **TASK-103**: Author `specs/spec.md` (functional requirements, bounded rollback, edge cases).
- [x] **TASK-104**: Author `specs/plan.md` (system architecture, PostgreSQL schema, REST contracts).
- [x] **TASK-105**: Author `specs/tasks.md` (phased implementation roadmap).
- [x] **TASK-106**: Commit specification artifacts to `specifications` branch, push to remote origin, and merge to `main`.

---

## Phase 2: Backend Implementation (Branch: `feature/backend`)
- [x] **TASK-201**: Scaffold Spring Boot 3 + Java 21 project with Maven Wrapper (`mvnw`, `mvnw.cmd`).
- [x] **TASK-202**: Setup `docker-compose.yml` for PostgreSQL 16+.
- [x] **TASK-203**: Create Flyway migration `V1__create_exchange_rates_table.sql` with unique index `(currency_code, rate_date)`.
- [x] **TASK-204**: Implement `ExchangeRateEntity` and `ExchangeRateRepository` with PostgreSQL `ON CONFLICT DO UPDATE` upsert support.
- [x] **TASK-205**: Implement `BnmXmlParser` with explicit XXE protection and robust parsing of `<ValCurs>` and `<Valute>`.
- [x] **TASK-206**: Implement `BnmClient` and `ExchangeRateService` with Bounded Rollback (up to 7 calendar days) and Tier 1 offline fallback to PostgreSQL cache.
- [x] **TASK-207**: Implement `CurrencyConversionService` using `BigDecimal`, nominal normalization, and high-precision intermediate math.
- [x] **TASK-208**: Implement REST controllers (`CurrencyController`, `ConversionController`) with Bean Validation and RFC 9457 `ProblemDetail`.
- [x] **TASK-209**: Implement `SecurityConfig` with CORS policies and HTTP security headers.
- [x] **TASK-210**: Commit backend implementation atomically and push to `origin feature/backend`.

---

## Phase 3: Frontend Implementation (Branch: `feature/frontend`)
- [x] **TASK-301**: Scaffold Vue 3 + Vite + TypeScript project.
- [x] **TASK-302**: Implement `SnapshotStorage` for persistent browser-side `localStorage` cache.
- [x] **TASK-303**: Implement Pinia store `useCurrencyStore` strictly managing ephemeral UI states (`idle`, `loading`, `success`, `error`, `offline`).
- [x] **TASK-304**: Implement `CurrencyInput` component with input formatting and client-side positive numeric validation.
- [x] **TASK-305**: Implement `CurrencySelect` component with search, nominal badges, and Swap action.
- [x] **TASK-306**: Implement `ConversionResult` displaying converted value, rate formula, bulletin date, and source indicator badge.
- [x] **TASK-307**: Implement `OfflineBanner` displaying active resilience mode (Tier 1 PostgreSQL cache or Tier 2 browser snapshot).
- [x] **TASK-308**: Commit frontend implementation atomically and push to `origin feature/frontend`.

---

## Phase 4: Testing & Verification (Branch: `feature/tests`)
- [x] **TASK-401**: Unit tests for `BnmXmlParser` (valid XML, malformed XML, empty documents, XXE payload rejection).
- [x] **TASK-402**: Unit tests for `CurrencyConversionService` (nominal scaling, cross-rates, identical currencies, precision rounding).
- [x] **TASK-403**: Unit tests for `ExchangeRateService` rollback behavior (bounded to 7 days).
- [x] **TASK-404**: Validation tests for REST controllers (RFC 9457 response on invalid, zero, or negative inputs).
- [ ] **TASK-405**: Integration tests with Testcontainers PostgreSQL verifying Flyway migrations and `ON CONFLICT DO UPDATE` (deferred in local dev due to Windows JVM attach deadlock on non-ASCII user paths; logic covered via lightweight stubs and fast unit tests).
- [x] **TASK-406**: Frontend unit/component tests for input validation and snapshot storage.
- [x] **TASK-407**: Single-command test runner script (`run-tests.bat` / `npm test`).
- [x] **TASK-408**: Commit tests atomically and push to `origin feature/tests`.

---

## Phase 5: Release & Reporting (Branch: `main`)
- [x] **TASK-501**: Merge `feature/tests` into `main`.
- [x] **TASK-502**: Execute end-to-end verification (live conversion, offline simulation, restart persistence).
- [x] **TASK-503**: Write comprehensive `REPORT.md` including Section 6 Verification Evidence.
- [x] **TASK-504**: Update `README.md` with setup and execution instructions.
- [x] **TASK-505**: Push final state to `origin main`.

---

## Phase 6: Security Hardening & Vulnerability Remediation (Branch: `main`)
- [x] **TASK-601**: Adversarial vulnerability audit across full stack (OWASP Top 10 / ASVS).
- [x] **TASK-602**: Eliminate JVM Attach API deadlock on Windows non-ASCII paths with lightweight stubs.
- [x] **TASK-603**: Harden amount and date range input validation (SEC-03, SEC-04).
- [x] **TASK-604**: Resolve transactional self-invocation bypass via `ExchangeRatePersistenceService` (SEC-05).
- [x] **TASK-605**: Implement single-flight concurrency lock, negative caching, and network fail-fast (SEC-01, SEC-02).
- [x] **TASK-606**: Enforce schema validation and exact decimal arithmetic in `SnapshotStorage` (SEC-06, SEC-07).
- [x] **TASK-607**: Harden HTTP security headers and environment configuration in `docker-compose.yml` (SEC-08, SEC-09).
- [x] **TASK-608**: Update project reporting `REPORT.md` and documentation with verification evidence.

