# Load Testing Report (k6)

> Note: Run the scripts under `load-tests/` against a live Docker Compose stack and fill in measured results below. Never fabricate benchmark numbers.

## Test Environment
- Stack: Docker Compose (`ticketing-backend`, `ticketing-postgres`, `ticketing-redis`, `ticketing-rabbitmq`)
- Tool: `k6`

## Scenarios
- **Scenario A — Event Browsing**: `k6 run load-tests/scenario-a-browsing.js` (Target: `p95 < 500ms`)
- **Scenario B — Seat Map**: `k6 run -e EVENT_ID=<uuid> load-tests/scenario-b-seatmap.js` (Target: `p95 < 500ms`)
- **Scenario C — Reservation Contention**: `k6 run -e EVENT_ID=<uuid> -e SEAT_ID=<uuid> -e TOKEN=<jwt> load-tests/scenario-c-contention.js` (Target: `p95 < 1000ms`)
- **Scenario D — Flash Sale (2,000 VUs / 50 seats / 60s)**: `k6 run -e EVENT_ID=<uuid> -e SEAT_IDS=<csv> -e TOKEN=<jwt> load-tests/scenario-d-flash-sale.js`
- **Scenario E — Webhook Burst**: `k6 run -e ORDER_CODE=<code> -e AMOUNT=<amt> -e SIGNATURE=<sig> load-tests/scenario-e-webhook-burst.js`

## Post-Test Correctness Verification
Run immediately after each load test:
```bash
docker exec -i ticketing-postgres psql -U postgres -d ticketing < load-tests/verify-correctness.sql
```
Every query must return `0 rows`.
