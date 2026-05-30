#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATIC_DIR="${ROOT}/backend/src/main/resources/static"

echo "Building Angular frontend…"
cd "${ROOT}/frontend"
npm run build

echo "Copying dist into Spring Boot static resources…"
rm -rf "${STATIC_DIR}"
mkdir -p "${STATIC_DIR}"
cp -r dist/frontend/browser/* "${STATIC_DIR}/"

echo "Done. Start the server with:"
echo "  cd backend && mvn spring-boot:run"
echo "Then open http://localhost:8080"
