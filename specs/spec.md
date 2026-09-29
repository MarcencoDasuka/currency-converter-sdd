# Functional & Technical Specification: Currency Converter SDD

## 1. Executive Summary & Purpose
The Currency Converter SDD application is a production-grade web application with a graphical user interface (GUI) providing reliable cross-currency exchange rate conversions using official data from the National Bank of Moldova (BNM).

The application is built strictly following Spec-Driven Development (SDD), ensuring all behaviors, math formulas, resilience modes, and security constraints are specified before implementation.

---

## 2. Domain Model & Mathematical Formulas

### 2.1 Currency & Exchange Rate Bulletin
- **Base Currency:** Moldovan Leu (`MDL`), currency code `MDL`, numeric code `498`, nominal `1`, rate against itself is identically `1.0000`.
- **Foreign Valutes:** Defined by BNM XML bulletin `<ValCurs Date="DD.MM.YYYY" name="Official exchange rates">`:
  - `ID`: external identifier (e.g., `47` for USD).
  - `NumCode`: 3-digit numeric code (e.g., `840`).
  - `CharCode`: 3-letter ISO-4217 alphabetic code (e.g., `USD`, `EUR`, `RON`, `UAH`, `GBP`).
  - `Nominal`: integer multiplier (e.g., `1`, `10`, `100`).
  - `Name`: human-readable currency name.
  - `Value`: exchange rate in MDL for `Nominal` units (e.g., `17.8250`).

### 2.2 Mathematical Model
1. **Unit Rate Normalization:**
   For any currency $C$:
   $$\text{unitRate}(C) = \frac{\text{rate}(C)}{\text{nominal}(C)}$$
   For the base currency MDL:
   $$\text{unitRate}(\text{MDL}) = 1.0$$
2. **Cross-Currency Conversion:**
   To convert an amount $A$ from Source Currency $S$ to Target Currency $T$:
   $$\text{convertedAmount} = A \times \left( \frac{\text{unitRate}(S)}{\text{unitRate}(T)} \right)$$
3. **Identity Conversion:**
   If $S == T$, then:
   $$\text{convertedAmount} = A$$
4. **Rounding Policy:**
   - Intermediate calculations: `MathContext.DECIMAL128`.
   - Result display/DTO: `setScale(4, RoundingMode.HALF_UP)`.

---

## 3. Data Ingestion & Bounded Rollback Specification

### 3.1 External Integration Endpoint
- Fixed trusted URI: `https://www.bnm.md/en/official_exchange_rates?get_xml=1&date={DD.MM.YYYY}`
- Transport: Spring `RestClient` or `WebClient` with 5-second connect timeout and 10-second read timeout.

### 3.2 Bulletin Validation & Rollback Algorithm
A bulletin is considered **VALID** if:
1. HTTP status is `200 OK`.
2. Response body is non-empty, well-formed XML with root element `<ValCurs>`.
3. The `<ValCurs>` contains at least 5 `<Valute>` child elements with positive numeric values.

Algorithm:
```
function resolveBulletin(requestedDate):
    targetDate = requestedDate
    attempts = 0
    MAX_ROLLBACK_DAYS = 7

    while attempts <= MAX_ROLLBACK_DAYS:
        check local PostgreSQL cache for targetDate
        if cached bulletin exists:
            return (bulletin, isCached=true, rollbackDays=attempts)

        try:
            bulletin = fetchFromBnm(targetDate)
            if isValid(bulletin):
                persistToPostgres(bulletin, targetDate)
                return (bulletin, isCached=false, rollbackDays=attempts)
        catch (NetworkException | TimeoutException | InvalidXmlException):
            // Fall through to rollback

        targetDate = targetDate.minusDays(1)
        attempts += 1

    // If live rollback exhausted, attempt latest available in local DB
    latestFromDb = findLatestInPostgres()
    if latestFromDb is not null:
        return (latestFromDb, isCached=true, rollbackDays=attempts, fallbackToLatest=true)

    throw BulletinUnavailableException("No valid rate bulletin found within rollback limits")
```

---

## 4. User Scenarios & Edge Cases

### 4.1 Happy Path Conversion
- **Given:** User enters positive amount `100.50`, selects Source `EUR` and Target `USD`.
- **When:** User clicks "Convert" (or submits form).
- **Then:** Application displays:
  - Converted amount: e.g. `108.7523 USD`.
  - Effective rate: `1 EUR = 1.0821 USD`.
  - Rate date: e.g. `2026-09-28`.
  - Source: `National Bank of Moldova`.

### 4.2 Non-Working Day / Holiday Request
- **Given:** User requests rates on a Sunday or official holiday where BNM publishes no bulletin.
- **When:** Rates are fetched.
- **Then:** System performs bounded rollback, retrieves the preceding valid bulletin (e.g. Friday), and displays an informational notice:
  *"Rates from preceding valid bulletin: 26.09.2026 (rollback applied: 2 days)"*.

### 4.3 Input Validation Edge Cases
1. **Empty Amount:** Convert button disabled; error displayed if touched: *"Amount is required"*.
2. **Zero Amount (`0.00`):** Rejected; error: *"Amount must be greater than zero"*.
3. **Negative Amount (`-50`):** Rejected; error: *"Amount must be a positive number"*.
4. **Non-Numeric Characters (`12a.5`):** Prevented by input filter; error: *"Invalid numeric format"*.
5. **Identical Currencies (`USD` → `USD`):** Handled gracefully with rate `1.0000` and no calculation error.

### 4.4 Two-Tier Offline Scenarios

#### Tier 1: Backend Offline from BNM (Server up, Internet down)
- BNM request times out.
- Backend queries PostgreSQL for the latest available rates.
- Response contains:
  ```json
  {
    "sourceCurrency": "USD",
    "targetCurrency": "EUR",
    "amount": 100.0,
    "convertedAmount": 92.1245,
    "rateDate": "2026-09-26",
    "source": "BNM (Cached in PostgreSQL)",
    "cached": true,
    "offline": true
  }
  ```
- Frontend displays an amber status badge: *"Operating on cached rates from PostgreSQL (26.09.2026)"*.

#### Tier 2: Frontend Offline from Backend (Full disconnection / Backend stopped)
- Client network request fails with `FetchError` or `ECONNREFUSED`.
- Frontend reads `lastKnownGoodSnapshot` from `localStorage`.
- Client performs local conversion using snapshot rates.
- Frontend renders `OfflineBanner`:
  *"Offline Mode: Server unreachable. Using local snapshot from 26.09.2026 14:30"*.

---

## 5. Security & Safety Contract
1. **SSRF Boundary:** User input accepts only `{amount, sourceCurrency, targetCurrency, date}`. Under no circumstances can the client specify a URL, hostname, port, or protocol.
2. **XXE Protection:** All XML parsers disable external DTDs and entities.
3. **RFC 9457 Problem Details:**
   ```json
   {
     "type": "https://api.currency-converter.local/errors/invalid-amount",
     "title": "Invalid Conversion Amount",
     "status": 400,
     "detail": "Amount must be strictly positive. Provided: -10.00",
     "instance": "/api/v1/convert"
   }
   ```
