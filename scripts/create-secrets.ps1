param(
    [string]$Namespace = "currency-platform",
    [switch]$PreserveExisting
)

$ErrorActionPreference = "Continue"

function New-RandomSecret([int]$Bytes = 24) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    } finally {
        $rng.Dispose()
    }
    return ([BitConverter]::ToString($buffer) -replace '-', '').ToLower()
}

kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f - | Out-Null
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($PreserveExisting) {
    $existing = kubectl get secret -n $Namespace -o jsonpath="{.items[*].metadata.name}"
    if ($LASTEXITCODE -eq 0 -and ($existing -split '\s+' -contains "currency-platform-secrets")) {
        Write-Host "Secret currency-platform-secrets already exists in namespace $Namespace (--PreserveExisting); skipping rotation." -ForegroundColor Yellow
        exit 0
    }
}

$pgPass = New-RandomSecret 16
$authPass = New-RandomSecret 16
$ratePass = New-RandomSecret 16
$convPass = New-RandomSecret 16
$alertPass = New-RandomSecret 16
$notifPass = New-RandomSecret 16
$rabbitPass = New-RandomSecret 16
$jwtSecret = New-RandomSecret 32

kubectl create secret generic currency-platform-secrets `
    --namespace $Namespace `
    --from-literal=POSTGRES_USER=postgres `
    --from-literal="POSTGRES_PASSWORD=$pgPass" `
    --from-literal="AUTH_DB_PASSWORD=$authPass" `
    --from-literal="RATE_DB_PASSWORD=$ratePass" `
    --from-literal="CONVERSION_DB_PASSWORD=$convPass" `
    --from-literal="ALERT_DB_PASSWORD=$alertPass" `
    --from-literal="NOTIFICATION_DB_PASSWORD=$notifPass" `
    --from-literal=RABBITMQ_USERNAME=currency_app `
    --from-literal="RABBITMQ_PASSWORD=$rabbitPass" `
    --from-literal="JWT_SECRET=$jwtSecret" `
    --dry-run=client -o yaml | kubectl apply -f -

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Created/updated secret currency-platform-secrets in namespace $Namespace (values intentionally not printed)." -ForegroundColor Green
