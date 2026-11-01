# HANDOFF_TO_CHATGPT

**Date:** 2026-11-01  
**Task:** TH-102 — Inventory service implementation  
**Prepared by:** Cursor Agent  
**MODE:** IMPLEMENTATION  
**Branch:** `feature/th-102-inventory-service`

---

## Database schema and migrations

Flyway `V1__inventory_schema.sql` in schema `inventory`:

| Table | Role |
| --- | --- |
| `stock` | `sku`, `on_hand`, `held` (+ check `on_hand >= held`) |
| `reservations` | id, order_id, sku, qty, state; partial unique index one `HELD` per order |
| `idempotency_records` | key PK, fingerprint, http_status, response_body |
| `operation_log` | append-only forensic log for oracle |

Seed: `SKU-MVP-1` on_hand=100, held=0.

## API implementation

Controller `/api/v1` matches `openapi-inventory.yaml`:

- `POST /reservations` (+ Idempotency-Key)
- `POST /reservations/{id}/release|consume`
- `GET /reservations/{id}`, `GET /stock/{sku}`

Uses `:contracts` DTOs / `ErrorCode` / header-only idempotency.

## Idempotency & transaction strategy

Documented in `docs/architecture/INVENTORY_CONCURRENCY.md`.

Per mutating call (single `@Transactional`):

1. `pg_advisory_xact_lock(hashtext(key))`
2. Load idempotency row `FOR UPDATE` → replay or `IDEMPOTENCY_KEY_CONFLICT`
3. `stock` / `reservations` `FOR UPDATE`
4. Mutate + insert idempotency + `operation_log`
5. Commit

## Concurrency evidence (tests)

Against real Postgres `localhost:5433` (Compose `thesis-postgres`):

| Test | Result |
| --- | --- |
| concurrent same Idempotency-Key (8 threads) | single reservationId; held=1 |
| 10 concurrent reserves on stock=5 | 5 success / 5 INSUFFICIENT_STOCK; no oversell |
| concurrent consume vs release | one 200, one 409; held=0; single terminal state |

**11/11** integration tests passed (0 skipped).

## Commands

```bash
cd infra && docker compose up -d   # Postgres :5433
cd .. && ./gradlew :services:inventory:test build
# CI also starts Postgres service; env INVENTORY_TEST_JDBC_URL=...
```

OpenAPI: `npx @redocly/cli@1.25.0 lint --config docs/contracts/redocly.yaml` (unchanged inventory paths).

## Acceptance checklist

| Criterion | Status |
| --- | --- |
| Taiga: tests + idempotency key | **Met** |
| Real Postgres persistence + Flyway | **Met** |
| Reserve/release/consume accounting | **Met** |
| Header idempotency + conflict | **Met** |
| Concurrent oversell / double-effect covered by tests | **Met** |
| No Payment/Shipping/Temporal/LLM | **Met** |

## Known limitations / unresolved

- Integration tests use shared Compose DB (reset tables in `@BeforeEach`), not Testcontainers (Docker detection flaky on this Mac; documented alternative + CI Postgres service).
- Exact charge amount still TH-103.
- Consume authorization “only after arrange” is orchestration responsibility (C-101-1), not enforced inside Inventory.

## Commits / branch

See `git log feature/th-102-inventory-service` after commits.  
`main` already contains TH-101/101.1 (pushed).

## Recommended next

**TH-103** Payment — charge/refund/UNKNOWN/resolve on same contract surface.
