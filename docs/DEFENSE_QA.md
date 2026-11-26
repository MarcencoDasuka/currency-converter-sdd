# Шпаргалка для защиты: Currency Converter Platform (Microservices + Kubernetes + CI/CD)

## 1. Краткий питч (30–60 секунд)

- **Что за проект:** Финансовое веб-приложение конвертации валют по официальным курсам **Национального банка Молдовы (BNM)** и система целевых алертов по курсам, переведённая из монолита (Spring Boot + Vue 3) в **микросервисную платформу (Lab 2)**, упакованная в **non-root Docker-контейнеры и оркестратор Kubernetes / Minikube (Lab 3)** и защищённая сквозным **CI/CD пайплайном в GitHub Actions**.
- **Главные доменные инварианты сохранены:** точная финансовая арифметика (`BigDecimal` + `MathContext.DECIMAL128`, учёт номинала `JPY/100`), защита XML от XXE, откат выходных/праздников до 7 дней, двухуровневый офлайн-режим, и полная обратная совместимость с Vue 3 UI (`/api/v1/currencies`, `/api/v1/convert`).

---

## 2. Из чего состоит микросервисная платформа (Lab 2)

1. **`api-gateway` (`:8080`):** единая точка входа, **fail-closed** проверка JWT, распределённый sliding-window **Redis Lua rate limiter** (`429 Too Many Requests` + `Retry-After`), очистка поддельных `X-User-*` заголовков, защита от path traversal (`..`, `%2e`, `%252e`), агрегированная готовность `/ready` и сквозной `X-Request-ID`.
2. **`auth-service` (`:8081`, БД `auth_db`):** регистрация, BCrypt-хеширование паролей, выпуск и валидация HS256 JWT (минимум 32 байта), профиль `/api/v1/auth/me`.
3. **`rate-service` (`:8082`, БД `rates_db`):** безопасный парсер XML BNM (XXE запрещён), кэширование в PostgreSQL (`INSERT ... ON CONFLICT DO UPDATE`), single-flight защита от Cache Stampede, negative cache, 7-дневный откат выходных и офлайн-фолбэк.
4. **`conversion-service` (`:8083`, БД `conversions_db`):** расчёт конвертаций на `BigDecimal`, graceful degradation при падении `rate-service` (локальный снимок курсов `local_rate_snapshots`), история конвертаций пользователя (защищена от IDOR) и **Transactional Outbox** (`ConversionCompleted` -> RabbitMQ).
5. **`alert-service` (`:8084`, БД `alerts_db`):** CRUD целевых алертов (`ABOVE` / `BELOW`), атомарная оценка (`SELECT ... FOR UPDATE` исключает двойное срабатывание) и **Transactional Outbox** (`RateAlertTriggered` -> RabbitMQ).
6. **`notification-service` (`:8085`, БД `notifications_db`):** асинхронный consumer RabbitMQ с **manual ACK**, дедупликацией `UNIQUE(event_id)` (`INSERT ... ON CONFLICT DO NOTHING`) и **Dead-Letter Queue (`currency.notifications.dlq`)** для битых (poison) сообщений.
7. **`frontend` (`:3001` / `:8080`):** Vue 3 + TypeScript на unprivileged Nginx (`UID 101`), проксирует `/api/*` на `api-gateway`.

---

## 3. Зачем нужен Kubernetes / Minikube (Lab 3)

- **Docker Compose** просто запускает контейнеры на одной машине и не умеет автоматически масштабировать сервисы под нагрузкой, распределять трафик между копиями одного сервиса или перезапускать умершие экземпляры по Liveness/Readiness-пробам в кластере.
- **Kubernetes (Minikube)** — полноценный оркестратор:
  - **Self-Healing:** удаление пода `conversion-service` автоматически запускает новый под через ReplicaSet за секунды.
  - **Горизонтальное масштабирование и балансировка:** `kubectl scale deployment/conversion-service --replicas=3`, а ClusterIP Service автоматически распределяет запросы по всем 3 подам.
  - **Изоляция и безопасность:** отдельный namespace `currency-platform`, non-root контейнеры (`UID 1000` / `UID 101`, `drop: ["ALL"]`), `Secret` и `ConfigMap` вместо захардкоженных значений, `NetworkPolicy` с политикой `default-deny-ingress`.
  - **Zero-downtime rolling updates & graceful shutdown:** `preStop: sleep 5` + `server.shutdown=graceful` + раздельные `startupProbe` / `readinessProbe` / `livenessProbe`.

---

## 4. Зачем нужен CI/CD (`.github/workflows/ci.yml`)

Пайплайн автоматически запускается на каждый `push` и `pull_request` в GitHub Actions и доказывает полную работоспособность продукта **без участия человека**:
1. **`backend-tests`:** гоняет все модульные и интеграционные тесты 7 Maven-модулей с реальными контейнерами **Testcontainers (PostgreSQL 16, RabbitMQ 3.13, Redis 7)** + регрессию старого монолита.
2. **`frontend-tests`:** проверяет Vue 3 (Vitest + строгий `vue-tsc` + production-сборка Vite).
3. **`k8s-and-contract-validation`:** валидирует все Kubernetes-манифесты через `kubeconform -strict`, проверяет контракты безопасности (`scripts/validate-contracts.py`: нет `localhost`, нет секретов, везде non-root, пробы, лимиты, LF-окончания) и схему `docker compose config -q`.
4. **`build-and-live-product-smoke`:** только если первые 3 этапа зелёные — собирает все 7 Docker-образов, поднимает **весь продукт целиком** с эфемерными секретами (`docker compose up -d --wait`) и прогоняет 9-шаговый живой E2E smoke-тест (`scripts/e2e-smoke.sh`: UI, `/health`, `/ready`, курсы валют, 401 fail-closed, регистрация/JWT, конвертация + RabbitMQ, алерт + RabbitMQ, IDOR 403).

---

## 5. Типовые вопросы преподавателя на защите

### Q1: Почему у каждого сервиса своя БД, хотя PostgreSQL-инстанс один?
В рамках одного кластера PostgreSQL реализован строгий паттерн **Database-per-Service** на уровне движка СУБД: 5 отдельных баз (`auth_db`, `rates_db`, `conversions_db`, `alerts_db`, `notifications_db`) и 5 непривилегированных ролей (`NOSUPERUSER`, `REVOKE ALL FROM PUBLIC`). Роль `auth_app` физически получает `permission denied` при попытке подключиться к `rates_db`. При росте нагрузки любую базу можно вынести на отдельный сервер без изменения единой строчки бизнес-кода.

### Q2: Что случится, если RabbitMQ упадёт в момент конвертации валюты?
Конвертация **не сломается** и событие **не потеряется**: `conversion-service` в одной локальной ACID-транзакции сохраняет саму конвертацию и запись в таблицу `outbox_events` (**Transactional Outbox**). Фоновый relay (`ConversionOutboxRelay`) с блокировкой `SELECT ... FOR UPDATE SKIP LOCKED` и экспоненциальным backoff доставит событие в RabbitMQ автоматически, как только брокер поднимется.

### Q3: Как гарантируется, что повторная доставка из RabbitMQ не создаст дубликаты уведомлений?
В `notification-service` включён `AcknowledgeMode.MANUAL`, а в таблице `notifications` стоит ограничение `UNIQUE(event_id)`. Запись выполняется нативным `INSERT ... ON CONFLICT (event_id) DO NOTHING`, и только после коммита в БД вызывается `channel.basicAck()`. Битые JSON-сообщения получают `basicNack(requeue=false)` и уходят в Dead-Letter Queue (`currency.notifications.dlq`), не вешая consumer.

### Q4: Что произойдёт, если упадёт `rate-service` или сайт BNM?
Сработает каскадная **Graceful Degradation**:
- Если упал BNM — `rate-service` вернёт последний сохранённый снимок из PostgreSQL (`offline: true, cached: true`).
- Если упал сам `rate-service` — `conversion-service` переключится на собственный локальный кэш `local_rate_snapshots` и продолжит выполнять конвертации без 500-ошибок.
- Если пропала связь с сервером полностью — Vue 3 `SnapshotStorage` посчитает конвертацию локально в браузере.

### Q5: Почему у `api-gateway` `readinessProbe` в K8s смотрит на `/health`, а не на `/ready`?
Эндпоинт `/ready` у Gateway — это диагностический агрегатор состояния всех 5 сервисов и Redis. Если бы Kubernetes снимал `api-gateway` с трафика при падении одного неосновного сервиса (например, `alert-service`), то пользователи не смогли бы даже открыть курсы или сделать конвертацию, несмотря на работающую Graceful Degradation. Поэтому K8s `readinessProbe` проверяет `/health` самого Gateway, а `/ready` используется для мониторинга и E2E-проверок.

### Q6: Как защитились от IDOR и подмены заголовков `X-User-Id`?
1. `api-gateway` всегда удаляет входящие `X-User-Id` / `X-User-Role` от клиента и подставляет только значения из валидированной JWT-подписи.
2. Каждый микросервис реализует **Defense-in-Depth**: сам проверяет подпись `Authorization: Bearer <JWT>` (игнорируя голые заголовки `X-User-*`), а при доступе по ID (`GET /conversions/{id}`, `DELETE /alerts/{id}`, `PATCH /notifications/{id}/read`) вызывает `AuthContext.requireOwnerOrAdmin()`, возвращая `403 Forbidden` в формате RFC 9457.
