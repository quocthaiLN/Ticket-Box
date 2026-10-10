#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Docker / Nginx (http://localhost:8080)"
(cd "$ROOT" && docker compose up -d)

echo "==> Backend: test"
(cd "$ROOT/backend" && ./mvnw test)

echo "==> Frontend: install + build"
(cd "$ROOT/frontend" && npm install && npm run build)

echo "Init done. Run ./dev.sh to start backend and frontend."