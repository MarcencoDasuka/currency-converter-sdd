# Currency Converter SDD (Spec-Driven Development)

Веб-приложение для конвертации валют с графическим интерфейсом, разработанное по методологии **Spec-Driven Development (SDD)** на основе официальных котировок **Национального Банка Молдовы (BNM)**.

Проект выполнен в рамках Лабораторной работы №3 с использованием связки **GitHub Spec Kit** + **Google Antigravity**.

---

## Технологический стек

- **Спецификации:** GitHub Spec Kit Markdown artifacts (`specs/`)
- **Бэкенд:** Java 21 LTS, Spring Boot 3.3.4, Spring Data JPA, Flyway, RestClient, Bean Validation, RFC 9457 Problem Details.
- **База данных:** PostgreSQL 16+ (Docker Compose), миграции Flyway.
- **Фронтенд:** Vue 3 (Composition API, `<script setup>`), TypeScript, Vite, Pinia (UI state), `SnapshotStorage` (`localStorage` offline persistence).
- **Тестирование:** JUnit 5, Mockito, AssertJ, Vitest.

---

## Архитектура и особенности

1. **Нормализация курсов с учетом номинала:**
   Каждая валюта приводится к единичному курсу молдавского лея (MDL):
   $$\text{unitRate}(\text{CUR}) = \frac{\text{CUR.rate}}{\text{CUR.nominal}}$$
   Вычисления выполняются в `BigDecimal` с контекстом `MathContext.DECIMAL128`. Итоговый результат округляется строго на границе ответа (`scale=4, HALF_UP`).
2. **Двухуровневый Offline-режим и кэширование:**
   - **Штатное кэширование (PostgreSQL):** Первый запрос за дату подгружает бюллетень НБМ и сохраняет его в БД. Все последующие запросы отдаются мгновенно из локального кэша (`cached: true`), статус отображается аккуратным бейджем в карточке.
   - **Уровень 1 (Авария шлюза BNM):** При сетевом сбое или недоступности сайта НБМ бэкенд возвращает последний доступный бюллетень из БД (`offline: true`) с показом предупреждающего баннера `Operating on Server-Side Cache`.
   - **Уровень 2 (Клиент отключен от бэкенда):** При полном обрыве связи фронтенд рассчитывает конвертацию прямо в браузере по снимку из `localStorage` (рациональная арифметика без потери точности) и отображает баннер `Full Offline Mode`.
3. **Bounded Rollback для нерабочих дней:**
   Если в запрошенную дату бюллетень BNM не публиковался (выходные/праздники), сервис автоматически откатывается на предшествующие календарные дни (до 7 дней) для поиска последнего валидного бюллетеня.
4. **Интерактивный DatePicker с бизнес-ограничениями:**
   - Позволяет рассчитывать исторические курсы для договоров, инвойсов и таможенного учета (от `01.01.1994` до `today`).
   - Блокирует выбор будущих дат в календаре и на бэкенде (`@PastOrPresent`).
   - Содержит быстрые пресеты (`[Сегодня]`, `[Вчера]`, `[Пятница]`).
   - Информирует о выходных днях (суббота/воскресенье) с подсказкой о правиле отката к предшествующей пятнице.
5. **Безопасность:**
   - Защита от XXE в XML-парсере (`disallow-doctype-decl`).
   - Архитектурная защита от SSRF (жестко зафиксированный URL BNM, типизированный `LocalDate`).
   - HTTP Security Headers (`nosniff`, `DENY`, CSP, `Permissions-Policy`) и строгий CORS.

---

## Запуск тестов одной командой

В соответствии с требованиями ТЗ, все модульные тесты бэкенда и фронтенда запускаются одной локальной командой:

### Windows:
```cmd
run-tests.bat
```

### Linux / macOS:
```bash
./run-tests.sh
```

---

## Запуск приложения

### Вариант 1: Полный запуск через Docker Compose (Рекомендуемый)

Все сервисы (PostgreSQL, Spring Boot бэкенд и Nginx с Vue 3 фронтендом) поднимаются одной командой:

```bash
docker compose up --build -d
```

- **Веб-интерфейс (GUI):** `http://localhost:3000`
- **REST API бэкенда:** `http://localhost:8080/api/v1/currencies`
- **База данных PostgreSQL:** `127.0.0.1:5432`

Остановить контейнеры:
```bash
docker compose down
```

---

### Вариант 2: Локальный запуск для разработки

Если вы хотите запускать бэкенд и фронтенд локально на хосте:

#### 1. Запуск базы данных PostgreSQL (Docker)
```bash
docker compose up -d postgres
```

#### 2. Запуск бэкенда (Spring Boot 3)
```cmd
cd backend
mvnw.cmd spring-boot:run
```
*(Для Linux/macOS: `./mvnw spring-boot:run`)*

Бэкенд стартует на `http://localhost:8080`.

#### 3. Запуск фронтенда (Vue 3 + Vite)
```cmd
cd frontend
npm.cmd install
npm.cmd run dev
```
*(Для Linux/macOS: `npm run dev`)*

Интерфейс доступен по адресу: `http://localhost:5173`.

---

## Структура репозитория

```
currency-converter-sdd/
├── specs/                           # Артефакты Spec Kit
│   ├── constitution.md              # Архитектурная конституция и инварианты
│   ├── spec.md                      # Функциональная спецификация и сценарии
│   ├── plan.md                      # Технический план и схема БД
│   └── tasks.md                     # Дорожная карта задач
├── backend/                         # Spring Boot 3 сервис
│   ├── Dockerfile                   # Multi-stage Dockerfile (Temurin 21 JRE)
│   ├── mvnw / mvnw.cmd              # Maven Wrapper
│   ├── pom.xml                      # Зависимости проекта
│   └── src/                         # Исходный код и тесты
├── frontend/                        # Vue 3 приложение
│   ├── Dockerfile                   # Multi-stage Dockerfile (Node 20 + Nginx Alpine)
│   ├── nginx.conf                   # Reverse proxy и SPA static router
│   ├── src/components/              # UI компоненты (DatePicker, CurrencyInput и др.)
│   ├── src/stores/                  # Pinia store (UI состояние)
│   ├── src/storage/                 # SnapshotStorage (localStorage offline fallback)
│   └── package.json
├── docker-compose.yml               # Полный стек (PostgreSQL 16 + Backend + Frontend)
├── run-tests.bat                    # Единый запуск тестов на Windows
├── run-tests.sh                     # Единый запуск тестов на Unix
├── REPORT.md                        # Итоговый аналитический отчёт
└── README.md
```
