# Currency Converter SDD (Spec-Driven Development)

A web application for currency conversion with a Graphical User Interface (GUI), built strictly following the **Spec-Driven Development (SDD)** methodology and powered by official exchange rates from the **National Bank of Moldova (BNM)**.

Developed as Lab #3 using **GitHub Spec Kit** + **Google Antigravity**.

---

## Technology Stack

- **Specifications:** GitHub Spec Kit Markdown artifacts (`specs/`)
- **Backend:** Java 21 LTS, Spring Boot 3.3.4, Spring Data JPA, Flyway, RestClient, Bean Validation, RFC 9457 Problem Details.
- **Database:** PostgreSQL 16+ (Docker Compose), Flyway migrations.
- **Frontend:** Vue 3 (Composition API, `<script setup>`), TypeScript, Vite, Pinia (ephemeral UI state), `SnapshotStorage` (`localStorage` offline persistence).
- **Testing:** JUnit 5, Mockito, AssertJ, Vitest.
- **Containerization:** Docker Compose, Multi-stage Dockerfiles (Maven Wrapper, Node 20, Nginx Alpine).

---

## Architecture & Key Features

1. **Nominal-Aware Rate Normalization:**
   Each currency is normalized to a unit rate against the Moldovan Leu (MDL):
   $$\text{unitRate}(\text{CUR}) = \frac{\text{CUR.rate}}{\text{CUR.nominal}}$$
   All arithmetic calculations use `BigDecimal` with `MathContext.DECIMAL128`. Final rounding is performed strictly at the response DTO boundary (`scale=4, HALF_UP`).
2. **Two-Tier Offline Resilience & Caching:**
   - **PostgreSQL Cache:** The first request for a given date fetches the BNM bulletin and caches it in PostgreSQL. Subsequent requests are served instantly from the local cache (`cached: true`), shown with an informative badge.
   - **Tier 1 (BNM Gateway Outage):** If the external BNM service times out or is unreachable, the backend serves the latest known persisted rates from PostgreSQL (`offline: true`) with an informative `Operating on Server-Side Cache` banner.
   - **Tier 2 (Client Disconnected from Backend):** In case of a total network outage, the Vue frontend computes conversions client-side using the latest snapshot from `localStorage` (exact rational arithmetic without float precision loss) and displays a `Full Offline Mode` banner.
3. **Bounded Rollback for Weekends and Holidays:**
   If no bulletin was published on the requested date (weekends or bank holidays), the service automatically performs a bounded rollback to preceding calendar days (up to 7 days) to find the latest valid bulletin.
4. **Interactive DatePicker with Business Validation:**
   - Supports historical rate calculations (from `1994-01-01` to `today`).
   - Rejects future dates both in the UI and via backend validation (`@PastOrPresent`).
   - Provides quick presets (`[Today]`, `[Yesterday]`, `[Friday]`).
   - Informs users about non-banking days (Saturday/Sunday) with rollback guidance.
5. **Security Hardening:**
   - XXE prevention in XML parser (`disallow-doctype-decl`).
   - Architectural SSRF immunity (hardcoded trusted BNM endpoint, typed `LocalDate`).
   - HTTP Security Headers (`nosniff`, `DENY`, CSP, `Permissions-Policy`) and strict CORS.

---

## Running Tests

Unit and integration tests for backend and frontend can be run using the standard project toolchains:

### Backend (Spring Boot 3 / JUnit 5):
```cmd
cd backend
mvnw.cmd test
```
*(For Linux/macOS: `./mvnw test`)*

### Frontend (Vue 3 / Vitest):
```cmd
cd frontend
npm.cmd test
```
*(For Linux/macOS: `npm test`)*

### Unified Test Runner (Linux / macOS / Bash):
```bash
./run-tests.sh
```

---

## Running the Application

### Option 1: Full Docker Compose Execution (Recommended)

All services (PostgreSQL 16, Spring Boot 3 backend, and Nginx with Vue 3 frontend) are started with a single command:

```bash
docker compose up --build -d
```

- **Web GUI:** `http://localhost:3000`
- **Backend REST API:** Proxied through Nginx reverse proxy at `http://localhost:3000/api/v1/currencies` (internally, the backend container listens on port 8080: `http://backend:8080`).
- **PostgreSQL Database:** Operates within the internal Docker Compose bridge network (`postgres:5432`); host port is intentionally not exposed to prevent port collisions and ensure network isolation.

To stop the containers:
```bash
docker compose down
```

---

### Option 2: Local Development Mode

To run backend and frontend directly on the host machine:

#### 1. Start PostgreSQL (Docker)
```bash
docker compose up -d postgres
```

#### 2. Start Backend (Spring Boot 3)
```cmd
cd backend
mvnw.cmd spring-boot:run
```
*(For Linux/macOS: `./mvnw spring-boot:run`)*

The backend will start at `http://localhost:8080`.

#### 3. Start Frontend (Vue 3 + Vite)
```cmd
cd frontend
npm.cmd install
npm.cmd run dev
```
*(For Linux/macOS: `npm run dev`)*

The frontend development server will be available at `http://localhost:5173`.

---

## Repository Structure

```
currency-converter-sdd/
├── specs/                           # Spec Kit artifacts
│   ├── constitution.md              # Architectural constitution & invariants
│   ├── spec.md                      # Functional specification & scenarios
│   ├── plan.md                      # Technical plan & database schema
│   └── tasks.md                     # Implementation task roadmap
├── backend/                         # Spring Boot 3 service
│   ├── Dockerfile                   # Multi-stage Dockerfile (Temurin 21 JDK + Maven Wrapper -> JRE 21)
│   ├── mvnw / mvnw.cmd              # Maven Wrapper
│   ├── pom.xml                      # Project dependencies & plugins
│   └── src/                         # Source code & unit tests
├── frontend/                        # Vue 3 application
│   ├── Dockerfile                   # Multi-stage Dockerfile (Node 20 + Nginx Alpine)
│   ├── nginx.conf                   # Reverse proxy & SPA static router
│   ├── src/components/              # UI components (DatePicker, CurrencyInput, etc.)
│   ├── src/stores/                  # Pinia store (ephemeral UI state)
│   ├── src/storage/                 # SnapshotStorage (localStorage offline fallback)
│   └── package.json
├── docker-compose.yml               # Full-stack Compose definition (PostgreSQL 16 + Backend + Frontend)
├── run-tests.sh                     # Unified test runner for Unix/Bash
├── REPORT.md                        # Final analytical report
└── README.md
```
