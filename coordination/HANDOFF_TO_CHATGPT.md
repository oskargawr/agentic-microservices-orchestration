# HANDOFF_TO_CHATGPT

**Date:** 2026-11-01  
**Task:** TH-104 — Shipping service (+ TH-103 acceptance verification)  
**Prepared by:** Cursor Agent  
**MODE:** IMPLEMENTATION  
**Branch:** `feature/th-104-shipping-service`

---

## TH-103 closure

Merged to `main` via PR #2 (`5bdf809`). Owner verification points 1–5 **passed** (see `PROJECT_STATE.md`). No TH-103 code expansion.

---

## TH-104 — Schema / migrations

Flyway `V1__shipping_schema.sql` (schema `shipping`):

- `shipments` — `shipment_id`, unique `order_id`, `state` ∈ {NONE, ARRANGED, CANCELLED}
- `idempotency_records`
- `operation_log` — `operation`, `shipment_id`, `order_id`, `idempotency_key`, `detail`, `created_at`

**State note:** `NONE` is conceptual (no row until arrange creates `ARRANGED`).

## Endpoints / contract

Per `openapi-shipping.yaml`, port **8083**:

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/shipments/arrange` | Idempotency-Key required |
| POST | `/api/v1/shipments/{id}/cancel` | Idempotency-Key required |
| GET | `/api/v1/shipments/{id}` | Snapshot for oracle |

Redocly lint: **valid**.

## State transitions / concurrency

See `docs/architecture/SHIPPING_CONCURRENCY.md`.

- arrange: advisory lock on key + unique `order_id`
- cancel: advisory lock + `FOR UPDATE` on shipment row
- Mutation + idempotency + operation_log in one transaction

## Idempotency

Same key + same fingerprint → replay stored HTTP body; mismatch → `IDEMPOTENCY_KEY_CONFLICT`.

## Operation log fields

`id`, `operation`, `shipment_id`, `order_id`, `idempotency_key`, `detail` (JSON text, e.g. arrange state / cancel from→to), `created_at`.

## Tests

```bash
cd infra && docker compose up -d   # Postgres :5433
./gradlew :services:shipping:test build
# 7/7 shipping tests, 0 skipped (when Postgres reachable)
# If Postgres down: @EnabledIf skips entire class
```

Also re-ran payment: **13/13**, 0 skipped.

## Build / lint

- `./gradlew build` — SUCCESS  
- `npx @redocly/cli lint docs/contracts/openapi-shipping.yaml --config docs/contracts/redocly.yaml` — valid

## Acceptance checklist

| Criterion | Status |
| --- | --- |
| Taiga: tests + compensation cancel | **Met** (integration) |
| Real Postgres | **Met** |
| Idempotency + concurrency | **Met** |
| No Temporal/LLM/runner | **Met** |

## Unresolved design

- None requiring Owner approval. `NONE` without persisted row matches inventory-style conceptual initial state (documented).

## Commits / next

| **Branch** | `feature/th-104-shipping-service` |
| **HEAD** | fdd727fcc6fbcfb4e555e9fb92545615154484e5 |
| **PR / merge** | Open PR → merge when CI green (do not force-push) |

**Next proposed:** TH-200 Variant A happy path (or parallel lit. TH-021/022).
