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
- [ ] **TASK-106**: Commit specification artifacts to `specifications` branch, push to remote origin, and merge to `main`.

---

## Phase 2: Backend Implementation (Branch: `feature/backend`)
- [ ] **TASK-201**: Scaffold Spring Boot 3 + Java 21 project with Maven Wrapper (`mvnw`, `mvnw.cmd`).
- [ ] **TASK-202**: Setup `docker-compose.yml` for PostgreSQL 16+.
- [ ] **TASK-203**: Create Flyway migration `V1__create_exchange_rates_table.sql` with unique index `(currency_code, rate_date)`.
- [ ] **TASK-204**: Implement `ExchangeRateEntity` and `ExchangeRateRepository` with PostgreSQL `ON CONFLICT DO UPDATE` upsert support.
- [ ] **TASK-205**: Implement `BnmXmlParser` with explicit XXE protection and robust parsing of `<ValCurs>` and `<Valute>`.
- [ ] **TASK-206**: Implement `BnmClient` and `ExchangeRateService` with Bounded Rollback (up to 7 calendar days) and Tier 1 offline fallback to PostgreSQL cache.
- [ ] **TASK-207**: Implement `CurrencyConversionService` using `BigDecimal`, nominal normalization, and high-precision intermediate math.
- [ ] **TASK-208**: Implement REST controllers (`CurrencyController`, `ConversionController`) with Bean Validation and RFC 9457 `ProblemDetail`.
- [ ] **TASK-209**: Implement `SecurityConfig` with CORS policies and HTTP security headers.
- [ ] **TASK-210**: Commit backend implementation atomically and push to `origin feature/backend`.

---

## Phase 3: Frontend Implementation (Branch: `feature/frontend`)
- [ ] **TASK-301**: Scaffold Vue 3 + Vite + TypeScript project.
- [ ] **TASK-302**: Implement `SnapshotStorage` for persistent browser-side `localStorage` cache.
- [ ] **TASK-303**: Implement Pinia store `useCurrencyStore` strictly managing ephemeral UI states (`idle`, `loading`, `success`, `error`, `offline`).
- [ ] **TASK-304**: Implement `CurrencyInput` component with input formatting and client-side positive numeric validation.
- [ ] **TASK-305**: Implement `CurrencySelect` component with search, nominal badges, and Swap action.
- [ ] **TASK-306**: Implement `ConversionResult` displaying converted value, rate formula, bulletin date, and source indicator badge.
- [ ] **TASK-307**: Implement `OfflineBanner` displaying active resilience mode (Tier 1 PostgreSQL cache or Tier 2 browser snapshot).
- [ ] **TASK-308**: Commit frontend implementation atomically and push to `origin feature/frontend`.

---

## Phase 4: Testing & Verification (Branch: `feature/tests`)
- [ ] **TASK-401**: Unit tests for `BnmXmlParser` (valid XML, malformed XML, empty documents, XXE payload rejection).
- [ ] **TASK-402**: Unit tests for `CurrencyConversionService` (nominal scaling, cross-rates, identical currencies, precision rounding).
- [ ] **TASK-403**: Unit tests for `ExchangeRateService` rollback behavior (bounded to 7 days).
- [ ] **TASK-404**: Validation tests for REST controllers (RFC 9457 response on invalid, zero, or negative inputs).
- [ ] **TASK-405**: Integration tests with Testcontainers PostgreSQL verifying Flyway migrations and `ON CONFLICT DO UPDATE`.
- [ ] **TASK-406**: Frontend unit/component tests for input validation and snapshot storage.
- [ ] **TASK-407**: Single-command test runner script (`run-tests.bat` / `npm test`).
- [ ] **TASK-408**: Commit tests atomically and push to `origin feature/tests`.

---

## Phase 5: Release & Reporting (Branch: `main`)
- [ ] **TASK-501**: Merge `feature/tests` into `main`.
- [ ] **TASK-502**: Execute end-to-end verification (live conversion, offline simulation, restart persistence).
- [ ] **TASK-503**: Write comprehensive `REPORT.md` including Section 6 Verification Evidence.
- [ ] **TASK-504**: Update `README.md` with setup and execution instructions.
- [ ] **TASK-505**: Push final state to `origin main`.
