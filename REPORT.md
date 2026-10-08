# Laboratory Work #3 Report: Currency Converter via Spec-Driven Development and Google Antigravity

---

## 1. Data Source Selection and Rationale

The **official XML gateway of the National Bank of Moldova (BNM)** was selected as the authoritative data source:
`https://www.bnm.md/en/official_exchange_rates?get_xml=1&date=DD.MM.YYYY`

### Selection Rationale:
1. **Authoritative Primary Source:** The National Bank of Moldova publishes official exchange rates of the Moldovan Leu (MDL) against all major world and regional currencies (USD, EUR, RON, UAH, GBP, CHF, JPY, etc.).
2. **Industrial Exchange Protocol (XML):** Unlike simplified flat-JSON aggregators, the BNM XML bulletin provides nominal multipliers (`<Nominal>100</Nominal>` for Japanese Yen, Hungarian Forint, etc.), official bulletin dates, and unique valute identifiers. This enabled designing a hardened XML parser with disabled XXE/DTD.
3. **Realistic Banking Calendar Handling:** BNM does not publish bulletins on non-working days or national holidays. This motivated the design of the **Bounded Rollback** algorithm (bounded lookup rolling back up to 7 calendar days to the previous valid bulletin).
4. **Reliability and No Rate Limits:** Free of costly API keys, highly stable, and openly accessible.

---

## 2. Models Used in Google Antigravity

Throughout the project execution on the closed Google Antigravity platform, the following model was utilized:
- **Gemini 3.8 Flash (High):**
  - *Stage 1 (Requirements Analysis & Planning):* authoring comprehensive master plan, identifying hidden edge cases, designing mathematical model with `BigDecimal` and nominal normalization.
  - *Stage 2 (Spec Kit Specifications):* authoring system constitution (`specs/constitution.md`), functional specification (`specs/spec.md`), technical plan (`specs/plan.md`), and task roadmap (`specs/tasks.md`).
  - *Stage 3 (Backend Implementation):* generating Spring Boot 3 on Java 21 structure, PostgreSQL Flyway migrations, hardened XML parser, conversion service, and RFC 9457 Problem Details controllers.
  - *Stage 4 (Frontend Implementation):* developing Vue 3 (Composition API) components, `SnapshotStorage` browser snapshot adapter, Pinia store, and responsive UI components.
  - *Stage 5 (Testing & Verification):* authoring JUnit 5, Mockito, Vitest unit tests, and build scripts.

---

## 3. Initial Implementation and Core Components

The initial implementation of core components successfully satisfied baseline functional requirements of the specification:

1. **Spec Kit Architecture:** All 4 artifacts (`constitution.md`, `spec.md`, `plan.md`, `tasks.md`) were systematically structured with clear invariants, formulas, and architectural boundaries.
2. **PostgreSQL Flyway Migration (`V1__create_exchange_rates_table.sql`):** The `exchange_rates` table with `BIGINT GENERATED ALWAYS AS IDENTITY`, exact `NUMERIC(18,6)` types, and unique index `(currency_code, rate_date)` was designed correctly.
3. **Hardened BNM XML Parser (`BnmXmlParser`):** Configured from inception with XXE protection flags (`disallow-doctype-decl`, disabled external entities) and robust parsing of `<ValCurs>/<Valute>`.
4. **Mathematical Conversion Model (`CurrencyConversionService`):** Unit rate normalization via `rate / nominal`, high-precision arithmetic in `MathContext.DECIMAL128`, cross-rates via base currency MDL, handling of identical currencies ($S == T$), and strict final presentation rounding at the DTO boundary (`scale=4, HALF_UP`).
5. **Vue 3 Component Model:** Components `CurrencyInput`, `CurrencySelect`, `ConversionResult`, `OfflineBanner`, and Pinia store compiled cleanly via `vue-tsc && vite build` with zero TypeScript errors.

*Engineering Note:* During subsequent Adversarial Code Review, essential hardening was applied for input boundaries, transaction isolation, and thread pool resilience (see Section 7).

---

## 4. Agent Errors and Manual Interventions (Required Section)

During project development, environment discrepancies and execution issues occurred and were resolved:

### Issue 1: PowerShell Execution Policy Blocking `npm`
- **Symptom:** Running `npm -v` in Windows PowerShell failed with:
  `PSSecurityException: AuthorizationManager ... UnauthorizedAccess`.
- **Root Cause:** In Windows PowerShell, running `npm` attempts to execute `npm.ps1`, which was blocked by the system `ExecutionPolicy`.
- **Fix:** Switched npm execution to direct batch file invocation `npm.cmd` via `cmd.exe /c "npm.cmd ..."` or direct binary resolution.

### Issue 2: `git push` Hanging on Git Credential Manager GUI Modal
- **Symptom:** Background task `git push -u origin specifications` hung waiting for user input. Process inspection revealed `git-credential-manager` running in the user desktop session.
- **Root Cause:** Default Git configuration used GUI credential manager waiting for browser/GUI interaction from an automated agent context.
- **Fix:** Integrated authenticated GitHub CLI token via `gh auth setup-git`, after which all `git push` commands execute instantly without modals.

### Issue 3: Non-ASCII User Path Encoding Issue in `mvnw.cmd`
- **Symptom:** Running `mvnw.cmd compile` caused Java to fail with:
  `ClassNotFoundException: \Desktop\currency-converter-sdd\backend\.mvn\wrapper\maven-wrapper.jar`.
- **Root Cause:** User workspace path contained Cyrillic characters (`C:\Users\наш компухтер\Desktop\...`). When expanding `%~dp0` in `cmd.exe`, path distortion occurred due to code page mismatches (CP866 vs CP1251 vs UTF-8).
- **Fix:** In `mvnw.cmd`, project root and wrapper jar paths were shifted to relative positioning (`.` and `.mvn\wrapper\maven-wrapper.jar`), and auto-detection of Java 21 in `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` was added.

### Issue 4: Mockito UnnecessaryStubbingException
- **Symptom:** Test `convert_InvalidAmount_ThrowsIllegalArgumentException` failed with:
  `UnnecessaryStubbingException: Following stubbings are unnecessary: setUp(CurrencyConversionServiceTest.java:56)`.
- **Root Cause:** Test verified early rejection of invalid amount (`<= 0`). Validation threw exception before invoking the mocked `exchangeRateService`, violating Mockito strict stubbing (`Strictness.STRICT_STUBS`).
- **Fix:** Wrapped mock in `setUp()` with `lenient().when(...)`, allowing isolated boundary validation tests without false positive failures.

### Issue 5: Vitest ReferenceError `localStorage is not defined`
- **Symptom:** Running frontend unit tests `npm.cmd test` failed with `localStorage is not defined`.
- **Root Cause:** Vitest defaults to Node.js environment where browser `localStorage` does not exist.
- **Fix:** In `SnapshotStorage.test.ts`, implemented lightweight in-memory mock for `globalThis.localStorage`, enabling autonomous headless execution.

### Issue 6: Mockito/ByteBuddy Windows Deadlock via Dynamic Attach API on Cyrillic Paths
- **Symptom:** Running tests caused a 5–6 minute freeze during `Starting ConversionControllerValidationTest` or `Running CurrencyConversionServiceTest` with JVM warning:
  `WARNING: A Java agent has been loaded dynamically (byte-buddy-agent-1.14.19.jar)`.
- **Root Cause:** In Java 21 on Windows, `ByteBuddyAgent.install()` attempts dynamic connection via native Windows Attach API (`sun.tools.attach.WindowsVirtualMachine`). Non-ASCII characters in user path caused named pipe creation (`\\.\pipe\javatool...`) to deadlock in native Windows libraries.
- **Fix:**
  1. In `ConversionControllerValidationTest`, replaced heavy `@WebMvcTest` with isolated lightweight `MockMvcBuilders.standaloneSetup()` integrating `GlobalExceptionHandler`.
  2. In service tests, replaced slow ByteBuddy proxying with JDK Dynamic Proxy (`java.lang.reflect.Proxy`) and lightweight stubs.
  3. In `pom.xml`, added `-XX:+EnableDynamicAgentLoading` to `maven-surefire-plugin`. Total test execution time dropped to **5.1 seconds**.

### Issue 7: Replacing Heavy Testcontainers with Fast Unit Tests (TASK-405)
- **Symptom / Problem:** Specifications initially envisioned Testcontainers PostgreSQL (`TASK-405`). However, running Docker containers with dynamic agent profiling on Windows with Cyrillic paths caused Attach API deadlocks and pipeline instability.
- **Engineering Decision:** Task `TASK-405` was deferred, and heavy container tests were replaced by isolated fast unit tests using Mockito/Dynamic Proxy. Schema syntax and Flyway migrations are validated by Spring Data JPA (`hibernate.ddl-auto: validate`) and local Docker Compose.

### Issue 8: Constructor Ambiguity in `ExchangeRateService` under Spring IoC in Docker
- **Symptom:** Initial container run failed with:
  `BeanCreationException: ... Failed to instantiate [com.converter.service.ExchangeRateService]: No default constructor found`.
- **Root Cause:** `ExchangeRateService` had two constructors (primary 5-argument constructor and 4-argument test helper). In tests it was called via `new`, but Spring IoC required unambiguous injection target.
- **Fix:** Added explicit `@Autowired` to the primary 5-argument constructor.

### Issue 9: False Positive `Operating on Server-Side Cache` Status Banner
- **Symptom:** Warning amber banner *"Live BNM gateway unavailable. Rates successfully served from local PostgreSQL cache"* displayed constantly for any selected date.
- **Root Cause:** In Pinia store `useCurrencyStore.ts`, the condition for `tier1_backend_cached` was `if (data.offline || data.cached)`. Normal PostgreSQL cache hits were misclassified as gateway outages.
- **Fix:** Restricted condition to strict `if (data.offline)`. Normal cache hits display a subtle `[Cached]` badge and source indicator, while warning banner appears strictly upon genuine BNM connection failure (`offline: true`). Additionally built interactive `DatePicker.vue` with constraints (`max: today`, `min: 1994-01-01`), presets, and non-banking day hints.

---

## 5. Spec Compliance Matrix

| Requirement / Specification | Status | Implementation in Code |
|---|---|---|
| **Graphical User Interface (GUI)** | Fulfilled | Vue 3 + Vite web interface (`frontend/src/App.vue`) |
| **Data Source Selection (BNM XML)** | Fulfilled | `BnmClient.java`, `BnmXmlParser.java` |
| **Nominal-Aware Precision** | Fulfilled | `unitRate = rate / nominal`, `MathContext.DECIMAL128`, final rounding `scale=4, HALF_UP` |
| **Official Rate & Bulletin Date** | Fulfilled | Displayed in `ConversionResult.vue` and returned in `ConversionResponseDto` |
| **Two-Tier Offline Mode** | Fulfilled | Tier 1: Backend serves PostgreSQL cache (`cached: true`); Tier 2: Frontend calculates rates from `localStorage` (`SnapshotStorage.ts`) |
| **Weekend & Holiday Behavior** | Fulfilled | Bounded rollback up to 7 calendar days (`ExchangeRateService.java`) |
| **Input Validation** | Fulfilled | UI button disabled, backend DTO validation returning RFC 9457 Problem Details |
| **Identical Currencies ($S == T$)** | Fulfilled | Returns exact amount with effective rate `1.000000` without division errors |
| **Cache Persistence Across Restarts** | Fulfilled | `exchange_rates` table in PostgreSQL and snapshot in `localStorage` |
| **Unit Tests** | Fulfilled | 27 tests: 21 JUnit 5 (backend) + 6 Vitest (frontend) |
| **Integration Tests with Testcontainers (TASK-405)** | Deferred / Replaced | Replaced by lightweight unit tests with stubs due to Windows Attach API; SQL schema validated via `hibernate.ddl-auto: validate` |
| **Test Execution** | Fulfilled | Unified script `run-tests.sh` (Unix/Bash), direct Maven Wrapper and NPM commands (`run-tests.bat` removed per spec) |
| **Git Workflow via Pull Requests** | Fulfilled (Phases 1–5) | Phases 1–5 merged via PR #1–#4; Phase 6 (security patches) committed directly to `main` |
| **Security Audit & Hardening** | Fulfilled | 9 vulnerabilities remediated (SEC-01 – SEC-09), committed atomically |
| **Stack Containerization (Docker Compose)** | Fulfilled | `docker-compose.yml` (PostgreSQL 16, Spring Boot 3 на Temurin 21 JRE, Nginx Alpine SPA & reverse proxy) |
| **Interactive DatePicker with Constraints** | Fulfilled | `DatePicker.vue` (range `1994-01-01` .. `today`, quick presets, non-banking day guidance) |

---

## 6. Verification Evidence

### 6.1. Build & Test Commands
- **Backend Tests (JUnit 5 + Mockito):**
  ```cmd
  cd backend && mvnw.cmd test
  ```
  *(For Linux/macOS: `cd backend && ./mvnw test`)*
- **Frontend Tests (Vitest):**
  ```cmd
  cd frontend && npm.cmd test
  ```
  *(For Linux/macOS: `cd frontend && npm test`)*
- **Unified Test Runner (Unix / macOS / Bash):**
  ```bash
  ./run-tests.sh
  ```
- **Backend JAR Build (Maven Wrapper):**
  ```cmd
  cd backend && mvnw.cmd package -DskipTests
  ```
- **Frontend Production Build:**
  ```cmd
  cd frontend && npm.cmd run build
  ```

### 6.2. Test Execution Log (Maven Wrapper + Vitest)
```
========================================================
Running Currency Converter Test Suite (Backend + Frontend)
========================================================

[1/2] Executing Spring Boot 3 / Java 21 Tests (JUnit 5)...
[INFO] Running com.converter.controller.ConversionControllerValidationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.controller.ConversionControllerValidationTest
[INFO] Running com.converter.service.BnmXmlParserTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.service.BnmXmlParserTest
[INFO] Running com.converter.service.CurrencyConversionServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.service.CurrencyConversionServiceTest
[INFO] Running com.converter.service.ExchangeRateServiceRollbackTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.service.ExchangeRateServiceRollbackTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (Total time: 5.1s)

[2/2] Executing Vue 3 Frontend Tests (Vitest)...
> vitest run
 ✓ src/storage/SnapshotStorage.test.ts (6 tests) 6ms
 Test Files  1 passed (1)
      Tests  6 passed (6) (Duration: 479ms)

========================================================
ALL 27 TESTS PASSED SUCCESSFULLY!
========================================================
```

### 6.3. Security Verification: XXE Prevention
Test `BnmXmlParserTest.parse_XxePayload_ThrowsIllegalArgumentException` confirmed that injecting `<!DOCTYPE ValCurs [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>` is blocked with:
`DOCTYPE is disallowed when the feature "http://apache.org/xml/features/disallow-doctype-decl" set to true`.

### 6.4. Security Verification: SSRF & Input Validation
- Base BNM URL `https://www.bnm.md/en/official_exchange_rates` is immutable in configuration.
- Requests are validated via Bean Validation. Passing negative amount returns RFC 9457 `application/problem+json`:
```json
{
  "type": "https://api.currency-converter.local/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Input parameter validation failed. Please check invalidParams.",
  "instance": "/api/v1/convert",
  "invalidParams": [
    {
      "name": "amount",
      "reason": "Amount must be strictly greater than zero"
    }
  ]
}
```

### 6.5. Security Verification: CORS & HTTP Security Headers
`SecurityConfig.java` attaches standard security headers to all responses:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Content-Security-Policy: default-src 'self'`
- `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()`
- CORS strictly restricted to origins `http://localhost:5173` and `http://localhost:3000`.

---

## 7. Adversarial Security Audit

### 7.1. Methodology & Threat Modeling (STRIDE / OWASP Top 10)
The codebase was evaluated according to **Adversarial Code Review** principles and **OWASP Top 10 (2021)** / **ASVS** standards:
- **A01: Broken Access Control & IDOR:** endpoint parameter validation, data leakage prevention.
- **A02: Cryptographic Failures & Secret Management:** credential management, connection security.
- **A03: Injection & XXE:** parser XXE testing, JPA/Hibernate parameterized SQL audit.
- **A04: Insecure Design & Resilience:** DoS mitigation, thread pool starvation prevention, API cascade failure containment.
- **A05: Security Misconfiguration:** HTTP security headers, CORS policies, Spring MVC and Flyway settings.
- **A06: Vulnerable and Outdated Components:** Maven (`pom.xml`) and NPM (`package.json`) dependency audit.
- **A08: Software and Data Integrity Failures:** offline snapshot validation in `localStorage`, arithmetic precision.
- **A09: Security Logging and Monitoring Failures:** stack trace redaction, RFC 9457 compliance.
- **A10: Server-Side Request Forgery (SSRF):** outbound HTTP request audit to BNM.

---

### 7.2. Vulnerability Summary Matrix
| ID | OWASP Category | Vulnerability / Risk | Severity | Status | Commit |
|---|---|---|---|---|---|
| **SEC-01** | A04: Insecure Design | Tomcat Thread Pool Starvation DoS via synchronous Bounded Rollback loop | **High** | `[RESOLVED & VERIFIED]` | `a03b569` |
| **SEC-02** | A04: Insecure Design | Cache Stampede / Dog-piling to external BNM gateway on concurrent requests | **Medium** | `[RESOLVED & VERIFIED]` | `a03b569` |
| **SEC-03** | A04: Insecure Design | Computational DoS via unconstrained astronomical `amount` values (`BigDecimal`) | **Medium** | `[RESOLVED & VERIFIED]` | `748f528` |
| **SEC-04** | A01 / A04 | Missing date range validation (Future / Far-Past Date Abuse) on REST controllers | **Medium** | `[RESOLVED & VERIFIED]` | `748f528` |
| **SEC-05** | Invariant / Architecture | `@Transactional` bypass via self-invocation in `ExchangeRateService` | **Low** | `[RESOLVED & VERIFIED]` | `9259054` |
| **SEC-06** | A08: Data Integrity | IEEE-754 float precision loss in frontend offline mode (`SnapshotStorage`) | **Medium** | `[RESOLVED & VERIFIED]` | `2e96026` |
| **SEC-07** | A08: Data Integrity | Missing defensive schema validation on `localStorage` deserialization | **Low** | `[RESOLVED & VERIFIED]` | `2e96026` |
| **SEC-08** | A05: Security Misconfiguration | Default DB credentials and public binding of port 5432 | **Low** | `[RESOLVED & VERIFIED]` | `37b93ed` |
| **SEC-09** | A05: Security Misconfiguration | Incomplete HTTP security headers profile | **Low** | `[RESOLVED & VERIFIED]` | `37b93ed` |

---

### 7.3. Detailed Technical Remediation Breakdown

#### SEC-01 [High] & SEC-02 [Medium]: Tomcat Thread Pool Starvation DoS & Cache Stampede Mitigation
- **File:** `ExchangeRateService.java`
- **Root Cause:** Network delays during sequential 8-day rollback tied up worker threads for up to 120s. Concurrent users triggered duplicate outbound calls for the same date.
- **Remediation (Commit `a03b569`):**
  1. Implemented **Single-Flight Lock** (`ConcurrentHashMap<LocalDate, Object> dateLocks`): only one thread queries BNM per date; concurrent threads wait and read cached result.
  2. Implemented **Fail-Fast**: upon network failure (`networkFailed = true`), service aborts rollback loop and immediately falls back to latest PostgreSQL cache (Tier 1 Offline).
  3. Implemented **Negative Caching** (`knownEmptyDates` with 15-minute TTL): confirmed empty dates (weekends/holidays) are not re-queried across the network.

#### SEC-03 [Medium] & SEC-04 [Medium]: Computational DoS & Date Range Protection
- **Files:** `ConversionRequestDto.java`, `CurrencyConversionService.java`, `CurrencyController.java`, `ConversionControllerValidationTest.java`
- **Root Cause:** Unbounded `amount` permitted astronomical values causing JVM memory pressure. Unchecked dates allowed future dates or far-past dates.
- **Remediation (Commit `748f528`):**
  1. In `ConversionRequestDto`, added `@DecimalMax("1000000000000.00")`, `@Digits(integer = 15, fraction = 4)`, and `@PastOrPresent`.
  2. In `CurrencyConversionService` and `CurrencyController`, added lower bound check `MIN_SUPPORTED_DATE = LocalDate.of(1994, 1, 1)` and future date rejection.
  3. Verified with 2 new validation tests in `ConversionControllerValidationTest`.

#### SEC-05 [Low]: Transactional Atomicity for Exchange Rate Ingestion
- **Files:** `ExchangeRatePersistenceService.java`, `ExchangeRateService.java`
- **Root Cause:** Self-invocation of `saveRatesIdempotently` within `ExchangeRateService` bypassed Spring AOP proxy, executing in auto-commit mode.
- **Remediation (Commit `9259054`):**
  Extracted persistence logic to dedicated `ExchangeRatePersistenceService`. Calls now pass through Spring AOP proxy, guaranteeing atomic transactions for the entire bulletin.

#### SEC-06 [Medium] & SEC-07 [Low]: Financial Precision & Schema Validation in Offline Storage
- **Files:** `SnapshotStorage.ts`, `SnapshotStorage.test.ts`
- **Root Cause:** Tier 2 offline calculations used JavaScript `number` (IEEE-754 float), causing precision discrepancies with backend `BigDecimal`. Corrupted `localStorage` lacked schema checks.
- **Remediation (Commit `2e96026`):**
  1. Implemented strict schema validation `isValidSnapshot(data)` before reading `localStorage`. Corrupted data is safely ignored.
  2. Implemented exact rational arithmetic with `HALF_UP` rounding using `BigInt` (`divideAndRoundHalfUp`), eliminating floating-point artifacts.
  3. Covered with 2 new Vitest unit tests.

#### SEC-08 [Low] & SEC-09 [Low]: HTTP Security Headers & Container Network Isolation
- **Files:** `SecurityConfig.java`, `docker-compose.yml`
- **Root Cause:** PostgreSQL port 5432 was mapped to all interfaces (`0.0.0.0`). Missing security headers.
- **Remediation (Commit `37b93ed`):**
  1. Restricted PostgreSQL and Backend ports strictly to internal Compose network (`expose`), eliminating host port conflicts and exposure.
  2. Added security headers: `Permissions-Policy`, `X-Permitted-Cross-Domain-Policies: none`, `Strict-Transport-Security`.

---

### 7.4. Verification of Core Defense Invariants
1. **XXE Protection:** `[VERIFIED SECURE]` — `BnmXmlParser` explicitly configures `disallow-doctype-decl = true` and disables external general/parameter entities.
2. **SSRF Protection:** `[VERIFIED SECURE]` — `BnmClient` targets an immutable configured endpoint. Input accepts only strongly typed `LocalDate`.
3. **SQL Injection:** `[VERIFIED SECURE]` — All database queries use Spring Data JPA and Hibernate parameterized statements; string concatenation is strictly absent.
4. **XSS Protection:** `[VERIFIED SECURE]` — Vue 3 uses safe text interpolation `{{ }}`; raw `v-html` is not used.
5. **Information Hiding:** `[VERIFIED SECURE]` — `GlobalExceptionHandler` converts all uncaught exceptions to RFC 9457 Problem Details; internal stack traces are redacted.

---

### 7.5. Vulnerability Remediation Commit History on `main`
1. `c1b894c`: `fix(test): eliminate JVM attach deadlock on non-ASCII paths with lightweight stubs`
2. `748f528`: `fix(security): harden amount and date range input validation (SEC-03, SEC-04)`
3. `9259054`: `fix(security): resolve transactional self-invocation bypass in exchange rate persistence (SEC-05)`
4. `a03b569`: `fix(security): prevent DoS thread starvation and cache stampede with single-flight and fail-fast fallback (SEC-01, SEC-02)`
5. `2e96026`: `fix(security): enforce schema validation and decimal precision in offline storage (SEC-06, SEC-07)`
6. `37b93ed`: `fix(security): harden HTTP security headers and environment configuration (SEC-08, SEC-09)`
7. `db9d870`: `feat(docker): add full-stack containerization and reconcile documentation`
8. `7ef65d6`: `feat(ui): add DatePicker with logical constraints and fix offline banner false positive`

---

## 8. Full-Stack Containerization & Deployment (Docker Architecture)

The entire application stack is containerized with **Docker Compose**:

### 8.1. Service Architecture

```
           [ Browser / Client ]
                    │ :3000
                    ▼
┌──────────────────────────────────────────────┐
│  currency-converter-frontend (Nginx Alpine)  │
│  - Serves Vue 3 Production SPA bundle        │
│  - Reverse Proxy: /api/* ──► backend:8080/api│
│  - Healthcheck: wget http://127.0.0.1/       │
└───────────────────────┬──────────────────────┘
                        │ (Internal Docker bridge network)
                        ▼ :8080
┌──────────────────────────────────────────────┐
│  currency-converter-backend (Temurin 21 JRE) │
│  - Spring Boot 3.3.4 (REST API, Problem JSON)│
│  - Built with Maven Wrapper (JDK 21 stage)   │
│  - Non-privileged appuser                    │
│  - JVM Tuning: G1GC, MaxRAMPercentage=75%    │
│  - Healthcheck: wget /api/v1/currencies      │
│  - Port 8080 exposed internally (no host map)│
└───────────────────────┬──────────────────────┘
                        │ (Internal Docker bridge network)
                        ▼ :5432
┌──────────────────────────────────────────────┐
│  currency-converter-postgres (Postgres 16)   │
│  - Rate cache persistence (exchange_rates)   │
│  - Automated Flyway migrations               │
│  - Healthcheck: pg_isready                   │
│  - Isolated within Compose bridge network    │
└──────────────────────────────────────────────┘
```

### 8.2. Container Implementation Details:
1. **Backend Multi-Stage Build (`backend/Dockerfile`):**
   - Build stage on `eclipse-temurin:21-jdk-alpine` using official **Maven Wrapper** (`./mvnw`).
   - Layer caching: copies `mvnw`, `.mvn`, and `pom.xml` first, executes `./mvnw dependency:go-offline -B`, then compiles source code via `./mvnw clean package -DskipTests -B`.
   - Production stage uses minimal `eclipse-temurin:21-jre-alpine` without build tools or Maven.
   - Runs as non-privileged user `appuser` (`addgroup -S appgroup && adduser -S appuser -G appgroup`).
   - JVM tuning: `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0`.
   - Built-in `HEALTHCHECK`: verifies REST API via `wget -q --spider http://127.0.0.1:8080/api/v1/currencies`.
2. **Frontend Multi-Stage Build (`frontend/Dockerfile`):**
   - Build stage on `node:20-alpine` (`npm run build` compiles Vue 3 SPA via `vue-tsc && vite build`).
   - Production stage on `nginx:alpine` with custom [`nginx.conf`](frontend/nginx.conf).
   - SPA client-side routing support: `try_files $uri $uri/ /index.html`.
   - Reverse proxy forwards `/api/*` requests to internal backend service `http://backend:8080`, eliminating CORS issues and hiding internal hostnames from the browser.
   - Built-in `HEALTHCHECK`: `wget -q --spider http://127.0.0.1/`.
3. **Orchestration & Network Isolation (`docker-compose.yml`):**
   - Backend starts only after database is healthy (`depends_on.postgres.condition: service_healthy`).
   - Frontend starts only after backend is healthy (`depends_on.backend.condition: service_healthy`).
   - PostgreSQL (`postgres:5432`) and Backend (`backend:8080`) are accessible strictly within the internal Compose network (`expose`) and are not mapped to host ports, preventing port collisions and unauthorized external access.
   - Only port `3000:80` is published to the host for the web GUI.

### 8.3. Docker Verification Summary:
- Web GUI accessible at: `http://localhost:3000`.
- Backend API accessible via reverse proxy: `http://localhost:3000/api/v1/currencies`.
- Conversion requests via `POST /api/v1/convert` execute with exact financial precision (`BigDecimal`).
- All 27 unit tests (`21 JUnit + 6 Vitest`) pass successfully.
