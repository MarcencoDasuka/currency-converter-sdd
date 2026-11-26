param(
    [switch]$SkipBuild,
    [switch]$SkipImageLoad,
    [string]$Namespace = "currency-platform"
)

$ErrorActionPreference = "Continue"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$MinikubeExe = "C:\Program Files\Kubernetes\Minikube\minikube.exe"
if (-not (Test-Path $MinikubeExe)) { $MinikubeExe = "minikube" }

$Images = @(
    "currency-platform/auth-service:latest",
    "currency-platform/rate-service:latest",
    "currency-platform/conversion-service:latest",
    "currency-platform/alert-service:latest",
    "currency-platform/notification-service:latest",
    "currency-platform/api-gateway:latest",
    "currency-platform/frontend:latest"
)

if (-not $SkipBuild) {
    Write-Host "=== [1/6] Building all container images via Docker Compose ===" -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        docker compose build
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
}

Write-Host "=== [2/6] Verifying Minikube cluster connectivity ===" -ForegroundColor Cyan
kubectl cluster-info | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Minikube not reachable; starting Minikube..." -ForegroundColor Yellow
    & $MinikubeExe start --driver=docker --cpus=4 --memory=4096
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not $SkipImageLoad) {
    Write-Host "=== [3/6] Loading local container images into Minikube ===" -ForegroundColor Cyan
    foreach ($img in $Images) {
        Write-Host "Loading $img ..."
        & $MinikubeExe image load $img
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}

Write-Host "=== [4/6] Applying Namespace, Secrets (preserving existing), ConfigMap and Infrastructure ===" -ForegroundColor Cyan
kubectl apply -f "$RepoRoot\k8s\namespace.yaml"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$PSScriptRoot\create-secrets.ps1" -Namespace $Namespace -PreserveExisting
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

kubectl apply -f "$RepoRoot\k8s\configmaps\app-config.yaml"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

kubectl delete job rabbitmq-init -n $Namespace --ignore-not-found | Out-Null
kubectl apply -f "$RepoRoot\k8s\infrastructure\postgres.yaml"
kubectl apply -f "$RepoRoot\k8s\infrastructure\rabbitmq.yaml"
kubectl apply -f "$RepoRoot\k8s\infrastructure\redis.yaml"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

kubectl rollout status statefulset/postgres -n $Namespace --timeout=180s
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Recreate idempotent init Jobs if they already existed
kubectl delete job postgres-roles-init -n $Namespace --ignore-not-found | Out-Null
kubectl apply -f "$RepoRoot\k8s\infrastructure\postgres-roles-init.yaml"
kubectl wait --for=condition=complete job/postgres-roles-init -n $Namespace --timeout=120s
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

kubectl rollout status deployment/rabbitmq -n $Namespace --timeout=180s
kubectl rollout status deployment/redis -n $Namespace --timeout=120s
kubectl wait --for=condition=complete job/rabbitmq-init -n $Namespace --timeout=120s
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== [5/6] Applying Microservices, API Gateway, Frontend and NetworkPolicies ===" -ForegroundColor Cyan
kubectl apply -f "$RepoRoot\k8s\services\services.yaml"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
kubectl apply -f "$RepoRoot\k8s\deployments"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
kubectl apply -f "$RepoRoot\k8s\networkpolicies"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== [6/6] Waiting for all deployments to become Ready ===" -ForegroundColor Cyan
$Deployments = @("auth-service", "rate-service", "conversion-service", "alert-service", "notification-service", "api-gateway", "frontend")
foreach ($dep in $Deployments) {
    Write-Host "Waiting for deployment/$dep ..."
    kubectl rollout status "deployment/$dep" -n $Namespace --timeout=240s
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

kubectl get pods,svc -n $Namespace
Write-Host "Deployment to Minikube namespace '$Namespace' completed successfully!" -ForegroundColor Green
