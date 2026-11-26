#!/bin/sh
set -eu

PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-${POSTGRES_USER:-postgres}}"
export PGPASSWORD="${PGPASSWORD:-${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}}"

: "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD is required}"
: "${RATE_DB_PASSWORD:?RATE_DB_PASSWORD is required}"
: "${CONVERSION_DB_PASSWORD:?CONVERSION_DB_PASSWORD is required}"
: "${ALERT_DB_PASSWORD:?ALERT_DB_PASSWORD is required}"
: "${NOTIFICATION_DB_PASSWORD:?NOTIFICATION_DB_PASSWORD is required}"

echo "Waiting for PostgreSQL at ${PGHOST}:${PGPORT}..."
until pg_isready -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" >/dev/null 2>&1; do
  sleep 1
done

# Ensure all 5 service databases exist
for db in auth_db rates_db conversions_db alerts_db notifications_db; do
  psql -v ON_ERROR_STOP=1 -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d postgres -v dbname="${db}" <<'EOSQL'
SELECT format('CREATE DATABASE %I', :'dbname')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'dbname')\gexec
EOSQL
done

configure_role() {
  db="$1"
  role="$2"
  pw="$3"

  psql -v ON_ERROR_STOP=1 -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d postgres \
    -v dbname="${db}" -v rolename="${role}" -v rolepw="${pw}" <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', :'rolename', :'rolepw')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'rolename')\gexec

SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', :'rolename', :'rolepw')\gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'dbname')\gexec
SELECT format('GRANT CONNECT, TEMP ON DATABASE %I TO %I', :'dbname', :'rolename')\gexec
EOSQL

  psql -v ON_ERROR_STOP=1 -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${db}" \
    -v rolename="${role}" <<'EOSQL'
SELECT format('ALTER SCHEMA public OWNER TO %I', :'rolename')\gexec
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'rolename')\gexec
EOSQL
}

configure_role auth_db auth_app "${AUTH_DB_PASSWORD}"
configure_role rates_db rate_app "${RATE_DB_PASSWORD}"
configure_role conversions_db conversion_app "${CONVERSION_DB_PASSWORD}"
configure_role alerts_db alert_app "${ALERT_DB_PASSWORD}"
configure_role notifications_db notification_app "${NOTIFICATION_DB_PASSWORD}"

echo "PostgreSQL DB-per-service roles configured successfully."
