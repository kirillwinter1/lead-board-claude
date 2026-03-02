#!/usr/bin/env bash
# run.sh — Lead Board Performance Testing Suite
# Usage: ./run.sh [seed|cleanup|smoke|load|stress|soak|multi|all]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
SEED_DIR="$SCRIPT_DIR/seed"
SCENARIOS_DIR="$SCRIPT_DIR/scenarios"

# Database connection (override via env vars)
DB_NAME="${DB_NAME:-leadboard}"
DB_USER="${DB_USER:-leadboard}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info()  { echo -e "${CYAN}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Check prerequisites
check_prereqs() {
    if ! command -v k6 &>/dev/null; then
        log_error "k6 not found. Install: brew install k6"
        exit 1
    fi
    if ! command -v psql &>/dev/null; then
        log_error "psql not found. Install PostgreSQL client."
        exit 1
    fi
}

# Run psql with connection params
run_psql() {
    PGPASSWORD="${DB_PASSWORD:-leadboard}" psql \
        -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        -v ON_ERROR_STOP=1 \
        "$@"
}

# Seed database
cmd_seed() {
    log_info "Seeding performance test data..."
    cd "$SEED_DIR"
    run_psql -f seed.sql
    log_ok "Database seeded successfully"

    # Verify
    local count
    count=$(run_psql -t -c "SELECT COUNT(*) FROM public.tenants WHERE slug LIKE 'perf-%'")
    log_info "Perf tenants: $(echo "$count" | tr -d ' ')"

    count=$(run_psql -t -c "SELECT COUNT(*) FROM tenant_perf_alpha.jira_issues")
    log_info "Issues in tenant_perf_alpha: $(echo "$count" | tr -d ' ')"
}

# Cleanup test data
cmd_cleanup() {
    log_info "Cleaning up performance test data..."
    cd "$SEED_DIR"
    run_psql -f cleanup.sql
    log_ok "Cleanup complete"
}

# Run k6 scenario
run_k6() {
    local scenario_name="$1"
    local scenario_file="$SCENARIOS_DIR/${scenario_name}.js"

    if [ ! -f "$scenario_file" ]; then
        log_error "Scenario not found: $scenario_file"
        exit 1
    fi

    mkdir -p "$RESULTS_DIR"
    local timestamp
    timestamp=$(date +%Y%m%d_%H%M%S)
    local result_file="$RESULTS_DIR/${scenario_name}_${timestamp}.json"

    log_info "Running ${scenario_name} scenario..."
    log_info "Results: $result_file"
    echo ""

    k6 run \
        --out "json=$result_file" \
        --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" \
        "$scenario_file"

    echo ""
    log_ok "${scenario_name} completed. Results: $result_file"
}

# Commands
cmd_smoke()  { run_k6 "smoke"; }
cmd_load()   { run_k6 "load"; }
cmd_stress() { run_k6 "stress"; }
cmd_soak()   { run_k6 "soak"; }
cmd_multi()  { run_k6 "multi-tenant"; }

cmd_reorder() { run_k6 "reorder-stress"; }

cmd_all() {
    cmd_seed
    echo ""
    cmd_smoke
    echo ""
    cmd_load
}

usage() {
    echo "Lead Board Performance Testing Suite"
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  seed      Seed database with test data (3 tenants, ~1.83M issues)"
    echo "  cleanup   Remove all test data"
    echo "  smoke     Run smoke test (1 VU, 30s)"
    echo "  load      Run load test (10-200 VUs, 5 min)"
    echo "  stress    Run stress test (0-500 VUs, 3 min)"
    echo "  soak      Run soak test (100 VUs, 30 min)"
    echo "  multi     Run multi-tenant test (3×50 VUs, 4 min)"
    echo "  reorder   Run epic reorder + forecast stress test (50 VUs, 5 min)"
    echo "  all       Run seed + smoke + load"
    echo ""
    echo "Environment variables:"
    echo "  DB_NAME     Database name (default: leadboard)"
    echo "  DB_USER     Database user (default: leadboard)"
    echo "  DB_PASSWORD Database password (default: leadboard)"
    echo "  DB_HOST     Database host (default: localhost)"
    echo "  DB_PORT     Database port (default: 5432)"
    echo ""
    echo "Prerequisites:"
    echo "  - k6 installed (brew install k6)"
    echo "  - PostgreSQL running"
    echo "  - Backend running on localhost:8080"
    echo "  - Rate limiter raised: APP_RATE_LIMIT_GENERAL=100000"
}

# Main
check_prereqs

case "${1:-}" in
    seed)    cmd_seed ;;
    cleanup) cmd_cleanup ;;
    smoke)   cmd_smoke ;;
    load)    cmd_load ;;
    stress)  cmd_stress ;;
    soak)    cmd_soak ;;
    multi)   cmd_multi ;;
    reorder) cmd_reorder ;;
    all)     cmd_all ;;
    *)       usage ;;
esac
