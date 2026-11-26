#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-currency-platform}"
PRESERVE_EXISTING="${PRESERVE_EXISTING:-false}"

rand_secret() {
  openssl rand -hex "$1"
}

kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

if [[ "${PRESERVE_EXISTING}" == "true" ]] && kubectl get secret currency-platform-secrets -n "${NAMESPACE}" >/dev/null 2>&1; then
  echo "Secret currency-platform-secrets already exists in ${NAMESPACE}; preserving existing values."
  exit 0
fi

kubectl create secret generic currency-platform-secrets \
  --namespace "${NAMESPACE}" \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal="POSTGRES_PASSWORD=$(rand_secret 16)" \
  --from-literal="AUTH_DB_PASSWORD=$(rand_secret 16)" \
  --from-literal="RATE_DB_PASSWORD=$(rand_secret 16)" \
  --from-literal="CONVERSION_DB_PASSWORD=$(rand_secret 16)" \
  --from-literal="ALERT_DB_PASSWORD=$(rand_secret 16)" \
  --from-literal="NOTIFICATION_DB_PASSWORD=$(rand_secret 16)" \
  --from-literal=RABBITMQ_USERNAME=currency_app \
  --from-literal="RABBITMQ_PASSWORD=$(rand_secret 16)" \
  --from-literal="JWT_SECRET=$(rand_secret 32)" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Secret currency-platform-secrets applied to namespace ${NAMESPACE}."
