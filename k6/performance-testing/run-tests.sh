#!/usr/bin/env bash
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORTS_DIR="${SCRIPT_DIR}/reports"
mkdir -p "${REPORTS_DIR}"

BASE_URL="${BASE_URL:-http://localhost:8082}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Automatically pick up backend/.env if present
if [ -f "${SCRIPT_DIR}/../../backend/.env" ]; then
    eval $(grep -E '^JWT_ACCESS_TOKEN=' "${SCRIPT_DIR}/../../backend/.env" | tr -d '\r')
    export JWT_ACCESS_TOKEN
fi

echo "========================================================"
echo " TicketBox K6 Performance & Load Testing Runner"
echo " Target Base URL: ${BASE_URL}"
echo " Reports Directory: ${REPORTS_DIR}"
echo "========================================================"

ensure_data_seeded() {
    echo ""
    echo ">>> Checking load test user data in PostgreSQL database..."
    local user_count
    user_count=$(docker exec ticketbox-postgres psql -U postgres -d ticketbox -t -c "SELECT COUNT(*) FROM users WHERE email LIKE 'loadtest_user_%';" 2>/dev/null | tr -d '[:space:]' || echo "0")
    if [ -z "${user_count}" ] || [ "${user_count}" -lt 10000 ]; then
        echo "⚠️ Database has ${user_count:-0} load test users (< 10,000). Running automatic generator & seeder..."
        node "${SCRIPT_DIR}/data/generate-data.js"
    else
        echo "✅ Verified: ${user_count} load test users are already present in DB."
    fi
}

run_catalog() {
    ensure_data_seeded
    echo ""
    echo ">>> Running Scenario 1: Isolated Public Catalog Benchmarking..."
    k6 run \
        -e BASE_URL="${BASE_URL}" \
        --summary-export="${REPORTS_DIR}/catalog_summary_${TIMESTAMP}.json" \
        "${SCRIPT_DIR}/scenarios/catalog-isolated.js"
    echo "✅ Catalog test finished. Summary: ${REPORTS_DIR}/catalog_summary_${TIMESTAMP}.json"
}

run_order() {
    ensure_data_seeded
    echo ""
    echo ">>> Running Scenario 2: Isolated Order & Payment Benchmarking..."
    k6 run \
        -e BASE_URL="${BASE_URL}" \
        --summary-export="${REPORTS_DIR}/order_summary_${TIMESTAMP}.json" \
        "${SCRIPT_DIR}/scenarios/order-isolated.js"
    echo "✅ Order test finished. Summary: ${REPORTS_DIR}/order_summary_${TIMESTAMP}.json"
}

run_e2e() {
    ensure_data_seeded
    echo ""
    echo ">>> Running Scenario 3: Full E2E Flash Sale Rush (1,000 VUs)..."
    k6 run \
        -e BASE_URL="${BASE_URL}" \
        --summary-export="${REPORTS_DIR}/e2e_flash_sale_summary_${TIMESTAMP}.json" \
        "${SCRIPT_DIR}/scenarios/flash-sale-e2e.js"
    echo "✅ E2E test finished. Summary: ${REPORTS_DIR}/e2e_flash_sale_summary_${TIMESTAMP}.json"
}

MODE="${1:-e2e}"

case "${MODE}" in
    catalog)
        run_catalog
        ;;
    order)
        run_order
        ;;
    e2e)
        run_e2e
        ;;
    all)
        run_catalog
        run_order
        run_e2e
        ;;
    *)
        echo "Usage: $0 [catalog|order|e2e|all]"
        exit 1
        ;;
esac

echo ""
echo "🎉 All requested test runs completed!"
