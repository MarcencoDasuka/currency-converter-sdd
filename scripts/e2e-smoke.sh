#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8095}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:3001}"
RUN_ID="$(date +%s)-$$"
USER_A_EMAIL="smoke-a-${RUN_ID}@example.com"
USER_B_EMAIL="smoke-b-${RUN_ID}@example.com"
TEST_PASS="Sm0keTestPass!"

echo "=== [1/9] Checking Frontend UI (${FRONTEND_URL}) ==="
curl -sf "${FRONTEND_URL}/" | grep -qi "<!doctype html"
echo "PASS: Frontend serves HTML"

echo "=== [2/9] Checking Gateway /health and /ready ==="
curl -sf "${GATEWAY_URL}/health" | grep -q '"status":"ok"'
curl -sf "${GATEWAY_URL}/ready" | grep -q '"status":"ready"'
echo "PASS: Gateway and all 5 downstream services + Redis are ready"

echo "=== [3/9] Checking public GET /api/v1/currencies via Frontend proxy ==="
CURRENCIES_JSON="$(curl -sf -H "X-Request-ID: smoke-cur-${RUN_ID}" "${FRONTEND_URL}/api/v1/currencies")"
echo "${CURRENCIES_JSON}" | grep -q '"code":"USD"'
echo "${CURRENCIES_JSON}" | grep -q '"code":"EUR"'
echo "${CURRENCIES_JSON}" | grep -q '"code":"MDL"'
echo "PASS: Public currencies endpoint works via Frontend -> API Gateway -> rate-service"

echo "=== [4/9] Checking fail-closed auth (401 RFC 9457) on protected route ==="
UNAUTH_CODE="$(curl -s -o /tmp/unauth.json -w '%{http_code}' "${GATEWAY_URL}/api/v1/alerts")"
test "${UNAUTH_CODE}" = "401"
grep -q '"status":401' /tmp/unauth.json
echo "PASS: Unauthenticated request to /api/v1/alerts rejected with 401 RFC 9457"

echo "=== [5/9] Registering User A and User B ==="
REG_A="$(curl -sf -X POST "${GATEWAY_URL}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${USER_A_EMAIL}\",\"password\":\"${TEST_PASS}\",\"fullName\":\"Smoke User A\"}")"
TOKEN_A="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "${REG_A}")"

REG_B="$(curl -sf -X POST "${GATEWAY_URL}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${USER_B_EMAIL}\",\"password\":\"${TEST_PASS}\",\"fullName\":\"Smoke User B\"}")"
TOKEN_B="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<< "${REG_B}")"
test -n "${TOKEN_A}" && test -n "${TOKEN_B}"
echo "PASS: Both users registered and received JWT tokens"

echo "=== [6/9] Executing authenticated conversion (USD -> EUR) with Outbox -> RabbitMQ ==="
CONV_HEADERS="/tmp/conv-headers-${RUN_ID}.txt"
CONV_BODY="$(curl -sf -D "${CONV_HEADERS}" -X POST "${GATEWAY_URL}/api/v1/convert" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN_A}" \
  -H "X-Request-ID: smoke-conv-${RUN_ID}" \
  -d '{"amount":100.00,"sourceCurrency":"USD","targetCurrency":"EUR"}')"
echo "${CONV_BODY}" | grep -q '"targetCurrency":"EUR"'
CONV_ID="$(grep -i '^x-conversion-id:' "${CONV_HEADERS}" | awk '{print $2}' | tr -d '\r\n')"
test -n "${CONV_ID}"
echo "PASS: Conversion saved with ID ${CONV_ID}"

echo "=== [7/9] Verifying IDOR protection (User B cannot access User A's conversion -> 403) ==="
IDOR_CODE="$(curl -s -o /tmp/idor.json -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN_B}" \
  "${GATEWAY_URL}/api/v1/conversions/${CONV_ID}")"
test "${IDOR_CODE}" = "403"
echo "PASS: IDOR blocked with 403 Forbidden"

echo "=== [8/9] Creating & evaluating price alert (EUR/MDL ABOVE 1.000000 -> triggers RabbitMQ event) ==="
ALERT_JSON="$(curl -sf -X POST "${GATEWAY_URL}/api/v1/alerts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN_A}" \
  -d '{"sourceCurrency":"EUR","targetCurrency":"MDL","direction":"ABOVE","targetRate":1.000000}')"
ALERT_ID="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "${ALERT_JSON}")"
EVAL_JSON="$(curl -sf -X POST "${GATEWAY_URL}/api/v1/alerts/evaluate" \
  -H "Authorization: Bearer ${TOKEN_A}")"
echo "${EVAL_JSON}" | grep -q '"triggeredCount":1'
echo "PASS: Alert ${ALERT_ID} evaluated and triggered"

echo "=== [9/9] Waiting for async RabbitMQ notifications in notification-service ==="
NOTIF_OK=0
for i in $(seq 1 15); do
  NOTIFS="$(curl -sf -H "Authorization: Bearer ${TOKEN_A}" "${GATEWAY_URL}/api/v1/notifications")"
  if echo "${NOTIFS}" | grep -q "ConversionCompleted" && echo "${NOTIFS}" | grep -q "RateAlertTriggered"; then
    NOTIF_OK=1
    break
  fi
  sleep 1
done
test "${NOTIF_OK}" = "1"
echo "PASS: Both async events (ConversionCompleted + RateAlertTriggered) delivered via RabbitMQ to notification-service!"
echo "=== ALL 9/9 E2E SMOKE CHECKS PASSED ==="
