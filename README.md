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
2. **Двухуровневый Offline-режим:**
   - **Уровень 1 (Бэкенд недоступен к BNM):** Бэкенд возвращает сохраненные данные из PostgreSQL с флагом `cached: true` и отображением бейджа источника.
   - **Уровень 2 (Клиент отключен от бэкенда):** Фронтенд перехватывает сетевой сбой, задействует последний снимок из `localStorage` и отображает `OfflineBanner`.
3. **Bounded Rollback для нерабочих дней:**
   Если в запрошенную дату бюллетень BNM не публиковался (выходные/праздники), сервис откатывается на предшествующие календарные дни (до 7 дней) для поиска последнего валидного бюллетеня.
4. **Безопасность:**
   - Защита от XXE в XML-парсере (`disallow-doctype-decl`).
   - Архитектурная защита от SSRF (жестко зафиксированный URL BNM, типизированный `LocalDate`).
   - HTTP Security Headers (`nosniff`, `DENY`, CSP) и строгий CORS.

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

## Локальный запуск приложения

### 1. Запуск базы данных PostgreSQL (Docker)
```bash
docker compose up -d
```

### 2. Запуск бэкенда (Spring Boot)
```cmd
cd backend
mvnw.cmd spring-boot:run
```
*(Для Linux/macOS: `./mvnw spring-boot:run`)*

Бэкенд стартует на `http://localhost:8080`.

### 3. Запуск фронтенда (Vue 3 + Vite)
```cmd
cd frontend
npm.cmd install
npm.cmd run dev
```

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
│   ├── mvnw / mvnw.cmd              # Maven Wrapper
│   ├── pom.xml                      # Зависимости проекта
│   └── src/                         # Исходный код и тесты
├── frontend/                        # Vue 3 приложение
│   ├── src/components/              # UI компоненты
│   ├── src/stores/                  # Pinia store (UI состояние)
│   ├── src/storage/                 # SnapshotStorage (localStorage)
│   └── package.json
├── docker-compose.yml               # PostgreSQL 16
├── run-tests.bat                    # Единый запуск тестов на Windows
├── run-tests.sh                     # Единый запуск тестов на Unix
├── REPORT.md                        # Итоговый аналитический отчёт
└── README.md
```
