param(
    [string]$Namespace = "currency-platform",
    [int]$LocalGatewayPort = 18090,
    [int]$LocalFrontendPort = 18091
)

$ErrorActionPreference = "Stop"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "=== [1/7] Inspecting Kubernetes resources in namespace $Namespace ===" -ForegroundColor Cyan
kubectl get pods,svc,pvc,networkpolicy -n $Namespace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$pfGw = Start-Process -FilePath "kubectl" -ArgumentList "port-forward", "-n", $Namespace, "svc/api-gateway", "${LocalGatewayPort}:8080" -PassThru -WindowStyle Hidden
$pfFe = Start-Process -FilePath "kubectl" -ArgumentList "port-forward", "-n", $Namespace, "svc/frontend", "${LocalFrontendPort}:8080" -PassThru -WindowStyle Hidden

try {
    Start-Sleep -Seconds 3
    $gwUrl = "http://127.0.0.1:$LocalGatewayPort"
    $feUrl = "http://127.0.0.1:$LocalFrontendPort"

    Write-Host "=== [2/7] Running full 9-step E2E API + RabbitMQ + IDOR verification against Minikube ===" -ForegroundColor Cyan
    & "$PSScriptRoot\e2e-smoke.ps1" -GatewayUrl $gwUrl -FrontendUrl $feUrl

    Write-Host "=== [3/7] Verifying X-Request-ID propagation in structured JSON logs ===" -ForegroundColor Cyan
    $traceId = "k8s-defense-trace-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
    Invoke-RestMethod -Uri "$gwUrl/api/v1/currencies" -Headers @{ "X-Request-ID" = $traceId } -TimeoutSec 15 | Out-Null
    Start-Sleep -Seconds 1
    $rateLogs = kubectl logs -n $Namespace -l app=rate-service --tail=50
    if (($rateLogs -join "`n") -notmatch $traceId) {
        throw "Did not find trace ID $traceId in rate-service JSON logs"
    }
    Write-Host "PASS: X-Request-ID '$traceId' correlated in rate-service JSON logs!" -ForegroundColor Green

    Write-Host "=== [4/7] Horizontal Scaling: scaling conversion-service to 3 replicas ===" -ForegroundColor Cyan
    kubectl scale deployment/conversion-service -n $Namespace --replicas=3 | Out-Null
    kubectl rollout status deployment/conversion-service -n $Namespace --timeout=180s
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    kubectl get pods -n $Namespace -l app=conversion-service

    Write-Host "=== [5/7] Verifying Service load distribution across conversion-service pods ===" -ForegroundColor Cyan
    $podHosts = @{}
    for ($i = 0; $i -lt 12; $i++) {
        $probe = kubectl exec -n $Namespace deploy/api-gateway -- wget -qO- http://conversion-service:8083/health
        $json = $probe | ConvertFrom-Json
        $podHosts[$json.hostname] = ($podHosts[$json.hostname] + 1)
    }
    Write-Host ("Observed conversion-service pod hostnames: " + ($podHosts | ConvertTo-Json -Compress))
    if ($podHosts.Keys.Count -lt 2) {
        throw "Expected requests distributed across at least 2 conversion-service pods"
    }
    Write-Host "PASS: Kubernetes Service distributed traffic across $($podHosts.Keys.Count) replicas!" -ForegroundColor Green

    Write-Host "=== [6/7] Self-Healing: deleting one conversion-service pod and confirming automatic recovery ===" -ForegroundColor Cyan
    $victimPod = (kubectl get pods -n $Namespace -l app=conversion-service -o jsonpath="{.items[0].metadata.name}")
    Write-Host "Deleting pod $victimPod ..."
    kubectl delete pod $victimPod -n $Namespace --wait=false | Out-Null
    Start-Sleep -Seconds 3
    kubectl rollout status deployment/conversion-service -n $Namespace --timeout=180s
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $readyCount = (kubectl get deployment conversion-service -n $Namespace -o jsonpath="{.status.readyReplicas}")
    if ([int]$readyCount -lt 3) { throw "Expected 3 ready replicas after self-healing, got $readyCount" }
    Write-Host "PASS: Kubernetes recreated deleted pod automatically ($readyCount/3 Ready)!" -ForegroundColor Green

    Write-Host "=== [7/7] Restoring conversion-service to baseline 2 replicas ===" -ForegroundColor Cyan
    kubectl scale deployment/conversion-service -n $Namespace --replicas=2 | Out-Null
    kubectl rollout status deployment/conversion-service -n $Namespace --timeout=120s
    $sw.Stop()
    Write-Host ("ALL 7/7 KUBERNETES DEFENSE VERIFICATION CHECKS PASSED in {0:N2}s!" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
} finally {
    if ($pfGw -and -not $pfGw.HasExited) { Stop-Process -Id $pfGw.Id -Force -ErrorAction SilentlyContinue }
    if ($pfFe -and -not $pfFe.HasExited) { Stop-Process -Id $pfFe.Id -Force -ErrorAction SilentlyContinue }
}
