# Currency Converter Platform (Microservices + Kubernetes + CI/CD)

A full-stack **Currency Conversion & Rate Alerting Platform** powered by official exchange rates from the **National Bank of Moldova (BNM)**, originally developed using **Spec-Driven Development (SDD)** and evolved into a production-hardened **Microservices Architecture**, **Kubernetes (Minikube)** deployment, and **GitHub Actions CI/CD** pipeline.

---

## Microservices Architecture

The application is decomposed into **5 isolated domain microservices**, a fail-closed **API Gateway**, a **Vue 3 SPA Frontend**, and supporting infrastructure (**PostgreSQL DB-per-service**, **RabbitMQ**, **Redis**):

| Component | Port | Database / Store | Responsibility |
| :--- | :---: | :--- | :--- |
| **`frontend`** | `3001` (`8080` internal) | `localStorage` offline snapshot | Vue 3 + TypeScript UI served by non-root Nginx, proxying `/api/*` to `api-gateway` |
| **`api-gateway`** | `8095` (`8080` internal) | Redis (`sliding-window ZSET`) | Single entry point, fail-closed JWT verification, distributed Lua rate limiting, header sanitization, path traversal guard, `/ready` aggregation |
| **`auth-service`** | `8081` | `auth_db` (`auth_app`) | User registration, BCrypt password hashing, HS256 JWT issuance, `/api/v1/auth/me` |
| **`rate-service`** | `8082` | `rates_db` (`rate_app`) | BNM XML ingestion (XXE-safe), PostgreSQL cache, single-flight lock, 7-day weekend/holiday rollback, offline fallback |
| **`conversion-service`** | `8083` (`replicas: 2` in K8s) | `conversions_db` (`conversion_app`) | Nominal-aware `BigDecimal` (`DECIMAL128`) conversions, local rate snapshot graceful degradation, user history, **Transactional Outbox** (`ConversionCompleted` -> RabbitMQ) |
| **`alert-service`** | `8084` | `alerts_db` (`alert_app`) | Target exchange-rate alerts (`ABOVE`/`BELOW`), `SELECT ... FOR UPDATE` atomic evaluation, **Transactional Outbox** (`RateAlertTriggered` -> RabbitMQ) |
| **`notification-service`** | `8085` | `notifications_db` (`notification_app`) | Manual-ACK RabbitMQ consumer with `UNIQUE(event_id)` deduplication, DLX/DLQ (`currency.notifications.dlq`), IDOR-protected inbox |

### Architectural Invariants
- **Financial Precision:** Strict `BigDecimal` + `MathContext.DECIMAL128`, nominal-aware unit rates ($\text{rate}/\text{nominal}$), rounding strictly at DTO boundary (`scale=4, HALF_UP`, `effectiveRate` `scale=6`), identity fast-path.
- **Database-per-Service Isolation:** 5 distinct PostgreSQL databases (`auth_db`, `rates_db`, `conversions_db`, `alerts_db`, `notifications_db`) with 5 least-privilege `NOSUPERUSER` roles (`auth_app`, `rate_app`, `conversion_app`, `alert_app`, `notification_app`) and `REVOKE ALL FROM PUBLIC`.
- **Reliable Eventing:** Transactional Outbox (`FOR UPDATE SKIP LOCKED`) in `conversion-service` and `alert-service` -> RabbitMQ `currency.events` topic exchange -> idempotent `INSERT ... ON CONFLICT (event_id) DO NOTHING` consumer in `notification-service` with Dead-Letter Queue (`currency.notifications.dlq`).
- **Security & Observability:** Fail-closed JWT HS256 (`JWT_SECRET >= 32 bytes`), defense-in-depth JWT verification in every service, IDOR/BOLA ownership checks (`403 Forbidden`), RFC 9457 `application/problem+json` errors without stack traces, `X-Request-ID` tracing in structured JSON logs with secret redaction, non-root containers (`UID 1000` / `UID 101`), Kubernetes `NetworkPolicy` default-deny ingress.

---

## Running Automated Tests

### Multi-Module Microservices Suite (JUnit 5 + Testcontainers PostgreSQL / RabbitMQ / Redis)
```powershell
cd services
.\mvnw.cmd test
```
*(Linux/macOS: `./mvnw test`)*

### Legacy Monolith Regression Suite (`backend/`)
```powershell
cd backend
.\mvnw.cmd test
```

### Vue 3 Frontend (Vitest + TypeScript Production Build)
```powershell
cd frontend
npm.cmd test
npm.cmd run build
```

### Static Security, K8s & Contract Validation
```powershell
python scripts/validate-contracts.py
```

---

## Running with Docker Compose

1. Create a local `.env` from `.env.example` and set strong random passwords & `JWT_SECRET` (>= 32 bytes):
   ```powershell
   Copy-Item .env.example .env
   ```
2. Build and start the full microservices platform:
   ```powershell
   docker compose up -d --build --wait
   ```
3. Verify live end-to-end health, conversions, RabbitMQ notifications, alerts, and IDOR isolation:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\e2e-smoke.ps1
   ```
   *(Linux/macOS: `./scripts/e2e-smoke.sh`)*

- **Web GUI:** `http://localhost:3001`
- **API Gateway:** `http://127.0.0.1:8095` (`/health`, `/ready`, `/docs`, `/api/v1/currencies`, `/api/v1/convert`)
- **RabbitMQ Management UI:** `http://127.0.0.1:15673`

Stop and clean up:
```powershell
docker compose down -v
```

---

## Kubernetes / Minikube Deployment

All manifests live under [`k8s/`](./k8s/) in isolated namespace **`currency-platform`**:

1. Deploy all images, secrets (generated locally via CSPRNG without printing values), PostgreSQL/RabbitMQ/Redis, init jobs, 7 application Deployments, Services, and NetworkPolicies:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\deploy-minikube.ps1
   ```
2. Run the 7-step live Kubernetes verification & defense demo (E2E API + RabbitMQ + IDOR, `X-Request-ID` log correlation, horizontal scaling `conversion-service` to 3 replicas, load distribution across pods, self-healing pod deletion, and restore):
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\demo-verification.ps1
   ```

---

## GitHub Actions CI/CD Pipeline

Defined in [`.github/workflows/ci.yml`](./.github/workflows/ci.yml), triggered on `push` and `pull_request`:
1. **`backend-tests`**: Runs `./mvnw test` across all 7 modules in `services/` (real Testcontainers PostgreSQL 16, RabbitMQ 3.13, Redis 7) plus `backend/` monolith tests.
2. **`frontend-tests`**: Runs `npm ci`, `npm test` (Vitest), and `npm run build` (`vue-tsc` + Vite bundle).
3. **`k8s-and-contract-validation`**: Runs `scripts/validate-contracts.py`, strict `kubeconform` schema validation on all `k8s/` manifests, and `docker compose config -q`.
4. **`build-and-live-product-smoke`**: Builds all 7 Docker images, boots the entire product with ephemeral CI secrets (`docker compose up -d --wait`), executes the 9-step live E2E smoke test (`scripts/e2e-smoke.sh`), and tears down cleanly.

---

## Repository Structure

```text
currency-converter-sdd/
├── .github/workflows/ci.yml        # Multi-stage CI/CD (tests -> K8s validation -> Docker build -> live E2E smoke)
├── services/                       # Multi-module Spring Boot 3 microservices platform
│   ├── pom.xml                     # Parent reactor POM (Java 21, Spring Boot 3.3.4)
│   ├── Dockerfile                  # Parameterized multi-stage non-root (UID 1000) Dockerfile
│   ├── common-lib/                 # Shared HS256 JWT, RFC 9457, X-Request-ID, JSON logging, DTOs & event envelopes
│   ├── auth-service/               # User auth & JWT issuance (auth_db)
│   ├── rate-service/               # BNM XML parser (XXE-safe), cache, rollback, offline fallback (rates_db)
│   ├── conversion-service/         # BigDecimal engine, graceful degradation, Outbox -> RabbitMQ (conversions_db)
│   ├── alert-service/              # Target rate alerts, atomic evaluation, Outbox -> RabbitMQ (alerts_db)
│   ├── notification-service/       # Manual-ACK RabbitMQ consumer, idempotency, DLQ (notifications_db)
│   └── api-gateway/                # Fail-closed proxy, Redis Lua rate limiter, /ready aggregator
├── frontend/                       # Vue 3 + TypeScript SPA + non-root Nginx (UID 101)
├── infrastructure/                 # PostgreSQL DB-per-service role scripts & RabbitMQ topology bootstrap
├── k8s/                            # Kubernetes manifests (namespace, configmap, infra, deployments, services, networkpolicies)
├── scripts/                        # Secret generation, Minikube deploy, E2E smoke, defense demo, static contract validator
├── docs/DEFENSE_QA.md              # Architecture presentation & defense Q&A guide
├── backend/                        # Original SDD monolith preserved for comparison & regression tests
└── docker-compose.yml              # Full microservices platform stack
```
