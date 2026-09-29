# Отчёт по Лабораторной работе №3: Конвертер валют через Spec-Driven Development и Google Antigravity

---

## 1. Выбор источника данных и обоснование

В качестве источника данных был выбран **официальный XML-шлюз Национального Банка Молдовы (BNM)**:
`https://www.bnm.md/en/official_exchange_rates?get_xml=1&date=DD.MM.YYYY`

### Обоснование выбора:
1. **Официальный первоисточник:** Национальный Банк Молдовы публикует официальный обменный курс молдавского лея (MDL) по отношению ко всем ключевым мировым и региональным валютам (USD, EUR, RON, UAH, GBP, CHF, JPY и др.).
2. **Промышленный протокол обмена (XML):** В отличие от упрощенных агрегаторов с плоским JSON, XML-бюллетень BNM содержит атрибуты номиналов (`<Nominal>100</Nominal>` для японской иены, венгерского форинта и др.), дату бюллетеня и уникальные идентификаторы. Это позволило реализовать защищенный парсер с отключением XXE/DTD.
3. **Реалистичная обработка банковского календаря:** BNM не публикует котировки по нерабочим и праздничным дням. Это позволило спроектировать алгоритм **Bounded Rollback** (ограниченный откат на глубину до 7 календарных дней к предыдущему валидному бюллетеню).
4. **Стабильность и отсутствие rate-limit:** Отсутствует необходимость регистрации платных API-ключей, шлюз стабилен и доступен.

---

## 2. Использованные модели в Google Antigravity

В ходе выполнения работы на закрытой платформе Google Antigravity использовалась модель:
- **Gemini 3.8 Flash (High):**
  - *Этап 1 (Анализ требований и планирование):* составление детального мастер-плана, выявление скрытых краевых случаев, проектирование математической модели с `BigDecimal` и номиналами.
  - *Этап 2 (Спецификации Spec Kit):* разработка конституции (`specs/constitution.md`), функциональной спецификации (`specs/spec.md`), технического плана (`specs/plan.md`) и декомпозиции задач (`specs/tasks.md`).
  - *Этап 3 (Реализация бэкенда):* генерация структуры Spring Boot 3 на Java 21, миграций Flyway для PostgreSQL, безопасного XML-парсера, сервиса конвертации и контроллеров RFC 9457 Problem Details.
  - *Этап 4 (Реализация фронтенда):* создание модулей Vue 3 (Composition API), адаптера `SnapshotStorage` для браузерного снимка, хранилища Pinia и адаптивных компонентов.
  - *Этап 5 (Тестирование и верификация):* написание модульных тестов JUnit 5, Mockito, Vitest и скриптов сборки.

---

## 3. Что агент собрал корректно с первой попытки

1. **Архитектура спецификаций Spec Kit:** Все 4 артефакта (`constitution.md`, `spec.md`, `plan.md`, `tasks.md`) были сгенерированы полностью согласованно, с четкими инвариантами и формулами.
2. **Flyway-миграция PostgreSQL (`V1__create_exchange_rates_table.sql`):** Таблица `exchange_rates` с `BIGINT GENERATED ALWAYS AS IDENTITY`, точными типами `NUMERIC(18,6)` и уникальным индексом `(currency_code, rate_date)` была спроектирована без ошибок.
3. **Безопасный XML-парсер BNM (`BnmXmlParser`):** С первой попытки корректно сконфигурированы флаги защиты от XXE (`disallow-doctype-decl`, отключение внешних сущностей) и разбор структуры `<ValCurs>/<Valute>`.
4. **Математическая модель конвертации (`CurrencyConversionService`):** Нормализация единицы курса через деление `rate / nominal`, точные расчеты в `MathContext.DECIMAL128`, кросс-курсы через базовую валюту MDL, обработка идентичных валют ($S == T$) и строгое финальное округление на внешней границе DTO (`scale=4, HALF_UP`).
5. **Компонентная модель Vue 3:** Компоненты `CurrencyInput`, `CurrencySelect`, `ConversionResult`, `OfflineBanner` и хранилище Pinia были реализованы и успешно собраны `vue-tsc && vite build` с первой попытки без единой ошибки типов TypeScript.

---

## 4. Ошибки агента и ручные вмешательства (Обязательный раздел)

В процессе выполнения возникли технические расхождения и ошибки среды, которые были зафиксированы и устранены:

### Ошибка 1: Блокировка вызовов `npm` политикой безопасности PowerShell
- **Симптом:** При попытке вызова `npm -v` в среде Windows PowerShell команда завершилась ошибкой:
  `PSSecurityException: AuthorizationManager ... UnauthorizedAccess`.
- **Причина:** В Windows PowerShell вызов `npm` пытается выполнить скрипт `npm.ps1`, выполнение которого заблокировано системной политикой `ExecutionPolicy`.
- **Исправление:** Вызовы npm переведены на прямой запуск исполняемого пакетного файла `npm.cmd` через `cmd.exe /c "npm.cmd ..."` или вызов команды напрямую.

### Ошибка 2: Зависание `git push` из-за интерактивного диалога Git Credential Manager
- **Симптом:** Фоновая задача `git push -u origin specifications` зависала на ожидании ввода. В диспетчере процессов был зафиксирован процесс `git-credential-manager` в сессии рабочего стола пользователя.
- **Причина:** Конфигурация Git по умолчанию использовала GUI-менеджер учетных данных, который ожидал клика в окне браузера/GUI при выполнении из автоматизированного контекста агента.
- **Исправление:** Была выполнена интеграция авторизованного токена GitHub CLI через команду `gh auth setup-git`, после чего все операции `git push` выполняются мгновенно без модальных окон.

### Ошибка 3: Проблема кодировки non-ASCII пути пользователя в `mvnw.cmd`
- **Симптом:** При выполнении `mvnw.cmd compile` Java завершалась ошибкой:
  `ClassNotFoundException: \Desktop\currency-converter-sdd\backend\.mvn\wrapper\maven-wrapper.jar`.
- **Причина:** Рабочий каталог пользователя содержит кириллические символы (`C:\Users\наш компухтер\Desktop\...`). При раскрытии переменной `%~dp0` в командном процессоре `cmd.exe` происходило искажение пути из-за различия кодовых страниц (CP866 vs CP1251 vs UTF-8).
- **Исправление:** В скрипте `mvnw.cmd` путь к директории проекта и jar-файлу был переведен на относительное позиционирование (`.` и `.mvn\wrapper\maven-wrapper.jar`), а также добавлен автопоиск установленной Java 21 в `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`.

### Ошибка 4: UnnecessaryStubbingException в тестах Mockito
- **Симптом:** Тест `convert_InvalidAmount_ThrowsIllegalArgumentException` завершился падением:
  `UnnecessaryStubbingException: Following stubbings are unnecessary: setUp(CurrencyConversionServiceTest.java:56)`.
- **Причина:** В тесте проверялось быстрое отклонение некорректной суммы (`<= 0`). Метод валидации выбросил исключение до обращения к замоканному сервису `exchangeRateService`, что вызвало срабатывание строгого режима Mockito (`Strictness.STRICT_STUBS`).
- **Исправление:** Мок в `setUp()` был обернут в `lenient().when(...)`, что позволило изолированно тестировать предварительную валидацию без ложных падений.

### Ошибка 5: ReferenceError `localStorage is not defined` в Vitest
- **Симптом:** Запуск модульных тестов фронтенда `npm.cmd test` упал с ошибкой `localStorage is not defined`.
- **Причина:** Тестовый раннер Vitest по умолчанию запускается в среде Node.js, где глобальный объект браузера `localStorage` отсутствует.
- **Исправление:** В тестовом файле `SnapshotStorage.test.ts` был внедрен легковесный in-memory mock для `globalThis.localStorage`, что позволило запускать тесты автономно без тяжелых браузерных эмуляторов.

---

## 5. Анализ соответствия спецификациям (Spec Compliance Matrix)

| Требование ТЗ / Спецификации | Статус | Реализация в коде |
|---|---|---|
| **Графический интерфейс (GUI)** | Выполнено | Vue 3 + Vite веб-интерфейс (`frontend/src/App.vue`) |
| **Выбор источника данных (BNM XML)** | Выполнено | `BnmClient.java`, `BnmXmlParser.java` |
| **Точность вычислений с номиналом** | Выполнено | `unitRate = rate / nominal`, `MathContext.DECIMAL128`, финальное округление `scale=4, HALF_UP` |
| **Официальный курс и дата бюллетеня** | Выполнено | Отображение в `ConversionResult.vue` и возврат в `ConversionResponseDto` |
| **Двухуровневый режим Offline** | Выполнено | Уровень 1: бэкенд отдает кэш PostgreSQL (`cached: true`); Уровень 2: фронтенд рассчитывает курс из `localStorage` (`SnapshotStorage.ts`) |
| **Поведение в выходные и праздники** | Выполнено | Bounded rollback до 7 календарных дней (`ExchangeRateService.java`) |
| **Валидация некорректного ввода** | Выполнено | Блокировка кнопки в UI, валидация DTO на бэкенде с возвратом RFC 9457 Problem Details |
| **Одинаковые валюты ($S == T$)** | Выполнено | Возврат точной суммы с курсом `1.000000` без ошибок деления |
| **Сохранение кэша между перезапусками** | Выполнено | Таблица `exchange_rates` в PostgreSQL и снимок в `localStorage` |
| **Unit-тесты** | Выполнено | 19 тестов JUnit 5/Mockito (бэкенд) + 4 теста Vitest (фронтенд) |
| **Запуск тестов одной командой** | Выполнено | Скрипты `run-tests.bat` (Windows) и `run-tests.sh` (Unix) |
| **Git-процесс через Pull Requests** | Выполнено | PR #1 (specs), PR #2 (backend), PR #3 (frontend), PR #4 (tests) успешно объединены в `main` |

---

## 6. Verification Evidence (Свидетельства верификации)

### 6.1. Команды сборки и тестов
- **Единый запуск всех тестов проекта:**
  ```cmd
  run-tests.bat
  ```
- **Запуск бэкенд-тестов по отдельности:**
  ```cmd
  cd backend && mvnw.cmd test
  ```
- **Сборка бэкенда (JAR):**
  ```cmd
  cd backend && mvnw.cmd package -DskipTests
  ```
- **Запуск тестов фронтенда по отдельности:**
  ```cmd
  cd frontend && npm.cmd test
  ```
- **Сборка фронтенда (Production bundle):**
  ```cmd
  cd frontend && npm.cmd run build
  ```

### 6.2. Протокол выполнения тестов (`run-tests.bat`)
```
========================================================
Running Currency Converter Test Suite (Backend + Frontend)
========================================================

[1/2] Executing Spring Boot 3 / Java 21 Tests (JUnit 5 + Mockito)...
[INFO] Running com.converter.controller.ConversionControllerValidationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.controller.ConversionControllerValidationTest
[INFO] Running com.converter.service.BnmXmlParserTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.service.BnmXmlParserTest
[INFO] Running com.converter.service.CurrencyConversionServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.service.CurrencyConversionServiceTest
[INFO] Running com.converter.service.ExchangeRateServiceRollbackTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.converter.service.ExchangeRateServiceRollbackTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

[2/2] Executing Vue 3 Frontend Tests (Vitest)...
> vitest run
 ✓ src/storage/SnapshotStorage.test.ts (4 tests) 4ms
 Test Files  1 passed (1)
      Tests  4 passed (4)

========================================================
ALL TESTS PASSED SUCCESSFULLY!
========================================================
```

### 6.3. Проверка безопасности: защита от XXE
Тест `BnmXmlParserTest.parse_XxePayload_ThrowsIllegalArgumentException` подтвердил, что инъекция `<!DOCTYPE ValCurs [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>` блокируется с исключением:
`DOCTYPE is disallowed when the feature "http://apache.org/xml/features/disallow-doctype-decl" set to true`.

### 6.4. Проверка безопасности: SSRF и валидация ввода
- Базовый URL BNM `https://www.bnm.md/en/official_exchange_rates` жестко зафиксирован в конфигурации.
- Запросы валидируются через Bean Validation. При передаче отрицательной суммы сервер возвращает RFC 9457 `application/problem+json`:
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

### 6.5. Проверка безопасности: CORS и HTTP Security Headers
В `SecurityConfig.java` настроен фильтр, добавляющий к ответам заголовки:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Content-Security-Policy: default-src 'self'`
- CORS строго ограничен источниками `http://localhost:5173` и `http://localhost:3000`.
