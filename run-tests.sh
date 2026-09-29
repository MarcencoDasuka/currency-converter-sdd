#!/usr/bin/env bash
set -e

echo "========================================================"
echo "Running Currency Converter Test Suite (Backend + Frontend)"
echo "========================================================"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

echo ""
echo "[1/2] Executing Spring Boot 3 / Java 21 Tests (JUnit 5 + Mockito)..."
cd "$DIR/backend"
./mvnw test

echo ""
echo "[2/2] Executing Vue 3 Frontend Tests (Vitest)..."
cd "$DIR/frontend"
npm test

echo ""
echo "========================================================"
echo "ALL TESTS PASSED SUCCESSFULLY!"
echo "========================================================"
