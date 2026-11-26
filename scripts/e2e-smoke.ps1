param(
    [string]$GatewayUrl = "http://127.0.0.1:8095",
    [string]$FrontendUrl = "http://127.0.0.1:3001"
)

$ErrorActionPreference = "Stop"
$RunId = "$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$PID"
$EmailA = "smoke-a-$RunId@example.com"
$EmailB = "smoke-b-$RunId@example.com"
$Pass = "Sm0keTestPass!"

Write-Host "=== [1/9] Checking Frontend UI ($FrontendUrl) ===" -ForegroundColor Cyan
$ui = Invoke-WebRequest -Uri "$FrontendUrl/" -UseBasicParsing -TimeoutSec 10
if ($ui.StatusCode -ne 200 -or $ui.Content -notmatch "<!doctype html") { throw "Frontend HTML check failed" }
Write-Host "PASS: Frontend UI returns 200 HTML" -ForegroundColor Green

Write-Host "=== [2/9] Checking API Gateway /health and /ready ===" -ForegroundColor Cyan
$health = Invoke-RestMethod -Uri "$GatewayUrl/health" -TimeoutSec 10
if ($health.status -ne "ok") { throw "Gateway /health not ok" }
$ready = Invoke-RestMethod -Uri "$GatewayUrl/ready" -TimeoutSec 15
if ($ready.status -ne "ready") { throw "Gateway /ready not ready: $($ready | ConvertTo-Json -Compress)" }
Write-Host "PASS: Gateway /ready verified all 5 services + Redis" -ForegroundColor Green

Write-Host "=== [3/9] Checking GET /api/v1/currencies via Frontend Nginx proxy ===" -ForegroundColor Cyan
$currencies = Invoke-RestMethod -Uri "$FrontendUrl/api/v1/currencies" -Headers @{ "X-Request-ID" = "ps-smoke-$RunId" } -TimeoutSec 15
$codes = $currencies.currencies | ForEach-Object { $_.code }
if ($codes -notcontains "USD" -or $codes -notcontains "EUR" -or $codes -notcontains "MDL") {
    throw "Missing expected currencies USD/EUR/MDL"
}
Write-Host "PASS: Currencies loaded ($($currencies.currencies.Count) currencies, rateDate=$($currencies.rateDate), offline=$($currencies.offline))" -ForegroundColor Green

Write-Host "=== [4/9] Checking fail-closed 401 RFC 9457 on protected /api/v1/alerts ===" -ForegroundColor Cyan
$got401 = $false
try {
    Invoke-WebRequest -Uri "$GatewayUrl/api/v1/alerts" -UseBasicParsing -TimeoutSec 10 | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401) { $got401 = $true }
}
if (-not $got401) { throw "Expected 401 on unauthenticated /api/v1/alerts" }
Write-Host "PASS: Fail-closed 401 verified" -ForegroundColor Green

Write-Host "=== [5/9] Registering User A and User B ===" -ForegroundColor Cyan
$regA = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/api/v1/auth/register" -ContentType "application/json" `
    -Body (@{ email = $EmailA; password = $Pass; fullName = "Smoke User A" } | ConvertTo-Json) -TimeoutSec 10
$regB = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/api/v1/auth/register" -ContentType "application/json" `
    -Body (@{ email = $EmailB; password = $Pass; fullName = "Smoke User B" } | ConvertTo-Json) -TimeoutSec 10
$headersA = @{ Authorization = "Bearer $($regA.accessToken)"; "X-Request-ID" = "smoke-conv-$RunId" }
$headersB = @{ Authorization = "Bearer $($regB.accessToken)" }
Write-Host "PASS: Both users registered and authenticated via JWT" -ForegroundColor Green

Write-Host "=== [6/9] Authenticated USD -> EUR conversion (Outbox -> RabbitMQ) ===" -ForegroundColor Cyan
$convResp = Invoke-WebRequest -Method Post -Uri "$GatewayUrl/api/v1/convert" -Headers $headersA -ContentType "application/json" `
    -Body (@{ amount = 100.00; sourceCurrency = "USD"; targetCurrency = "EUR" } | ConvertTo-Json) -UseBasicParsing -TimeoutSec 15
$convBody = $convResp.Content | ConvertFrom-Json
$convId = $convResp.Headers["X-Conversion-Id"]
if ([string]::IsNullOrWhiteSpace($convId)) { throw "Missing X-Conversion-Id header" }
Write-Host "PASS: Converted 100 USD -> $($convBody.convertedAmount) EUR (id=$convId)" -ForegroundColor Green

Write-Host "=== [7/9] Verifying IDOR protection (User B -> User A conversion = 403) ===" -ForegroundColor Cyan
$got403 = $false
try {
    Invoke-WebRequest -Uri "$GatewayUrl/api/v1/conversions/$convId" -Headers $headersB -UseBasicParsing -TimeoutSec 10 | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) { $got403 = $true }
}
if (-not $got403) { throw "Expected 403 IDOR block" }
Write-Host "PASS: IDOR blocked with 403 Forbidden" -ForegroundColor Green

Write-Host "=== [8/9] Creating and evaluating target rate alert (EUR/MDL ABOVE 1.00) ===" -ForegroundColor Cyan
$alert = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/api/v1/alerts" -Headers $headersA -ContentType "application/json" `
    -Body (@{ sourceCurrency = "EUR"; targetCurrency = "MDL"; direction = "ABOVE"; targetRate = 1.000000 } | ConvertTo-Json) -TimeoutSec 10
$eval = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/api/v1/alerts/evaluate" -Headers $headersA -TimeoutSec 15
if ($eval.triggeredCount -lt 1) { throw "Expected alert to trigger" }
Write-Host "PASS: Alert $($alert.id) evaluated and triggered ($($eval.triggeredCount))" -ForegroundColor Green

Write-Host "=== [9/9] Waiting for async RabbitMQ notifications in notification-service ===" -ForegroundColor Cyan
$foundBoth = $false
for ($i = 0; $i -lt 15; $i++) {
    $notifs = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/notifications" -Headers $headersA -TimeoutSec 10
    $types = $notifs | ForEach-Object { $_.eventType }
    if (($types -contains "ConversionCompleted") -and ($types -contains "RateAlertTriggered")) {
        $foundBoth = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $foundBoth) { throw "Did not receive both RabbitMQ notifications within 15s" }
Write-Host "PASS: Both ConversionCompleted & RateAlertTriggered received asynchronously via RabbitMQ!" -ForegroundColor Green
Write-Host "=== ALL 9/9 E2E SMOKE CHECKS PASSED ===" -ForegroundColor Green
