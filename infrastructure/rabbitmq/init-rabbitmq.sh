#!/bin/sh
set -eu

RABBIT_HOST="${RABBITMQ_HOST:-rabbitmq}"
RABBIT_MGMT_PORT="${RABBITMQ_MGMT_PORT:-15672}"
RABBIT_USER="${RABBITMQ_USERNAME:?RABBITMQ_USERNAME is required}"
RABBIT_PASS="${RABBITMQ_PASSWORD:?RABBITMQ_PASSWORD is required}"
BASE="http://${RABBIT_HOST}:${RABBIT_MGMT_PORT}/api"

echo "Waiting for RabbitMQ management API at ${BASE}..."
until curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" "${BASE}/overview" >/dev/null 2>&1; do
  sleep 2
done

# Declare topic exchange currency.events
curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" -H "content-type:application/json" -X PUT \
  "${BASE}/exchanges/%2f/currency.events" \
  -d '{"type":"topic","durable":true,"auto_delete":false}'

# Declare dead-letter exchange currency.events.dlx
curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" -H "content-type:application/json" -X PUT \
  "${BASE}/exchanges/%2f/currency.events.dlx" \
  -d '{"type":"direct","durable":true,"auto_delete":false}'

# Declare DLQ
curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" -H "content-type:application/json" -X PUT \
  "${BASE}/queues/%2f/currency.notifications.dlq" \
  -d '{"durable":true,"auto_delete":false}'

# Declare main notifications queue with DLX routing
curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" -H "content-type:application/json" -X PUT \
  "${BASE}/queues/%2f/currency.notifications" \
  -d '{"durable":true,"auto_delete":false,"arguments":{"x-dead-letter-exchange":"currency.events.dlx","x-dead-letter-routing-key":"notifications.dead"}}'

# Bind routing keys
curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" -H "content-type:application/json" -X POST \
  "${BASE}/bindings/%2f/e/currency.events/q/currency.notifications" \
  -d '{"routing_key":"conversion.completed"}'

curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" -H "content-type:application/json" -X POST \
  "${BASE}/bindings/%2f/e/currency.events/q/currency.notifications" \
  -d '{"routing_key":"alert.triggered"}'

curl -sf -u "${RABBIT_USER}:${RABBIT_PASS}" -H "content-type:application/json" -X POST \
  "${BASE}/bindings/%2f/e/currency.events.dlx/q/currency.notifications.dlq" \
  -d '{"routing_key":"notifications.dead"}'

echo "RabbitMQ topology initialized successfully."
