# HANDOFF_TO_CHATGPT

**Date:** 2026-11-01  
**Task:** TH-103 — Payment service  
**Prepared by:** Cursor Agent  
**MODE:** IMPLEMENTATION  
**Branch:** `feature/th-103-payment-service`

---

## Schema / migrations

Flyway `V1__payment_schema.sql` (schema `payment`):

- `payments` — oracle ground truth `state`; unique `order_id`
- `idempotency_records`
- `operation_log` — includes F3 `persistedState` vs `observedResponse`

## Endpoints

Per `openapi-payment.yaml`: charge, refund, resolve, GET snapshot. Port **8082**.

## State semantics

As protocol: CAPTURED / FAILED / UNKNOWN / PENDING / REFUNDED; refund only CAPTURED; inconclusive resolve preserves UNKNOWN/PENDING.

## Idempotency / concurrency

See `docs/architecture/PAYMENT_CONCURRENCY.md`. Advisory locks + unique order_id; tested concurrent charge/refund/resolve.

## F3 mechanism (demonstrated)

`thesis.payment.fault-injection-enabled=true` (profile `test`) + header `X-Thesis-Fault: F3_CAPTURE_LOST_RESPONSE`:

1. Persist `CAPTURED`
2. Charge HTTP returns `UNKNOWN`
3. GET/oracle sees `CAPTURED`
4. Resolve returns `CAPTURED`
5. Log distinguishes observed vs persisted

Not enabled by default in normal runtime.

## Tests

```bash
cd infra && docker compose up -d
./gradlew :services:payment:test build
# 13/13 payment tests, 0 skipped
```

## Acceptance checklist

| Criterion | Status |
| --- | --- |
| Taiga: Paid/Failed/UNKNOWN + query | **Met** (CAPTURED/FAILED/UNKNOWN + GET) |
| F3 integration-tested | **Met** |
| Idempotency + concurrency tested | **Met** |
| Real Postgres | **Met** |
| No Shipping/Temporal/LLM | **Met** |

## Limitations

- Fault header is experimental/test control (gated); not a public product API.
- Default charge without fault injection = CAPTURED (deterministic).

## Commits / next

HEAD recorded after push. **Next:** TH-104 Shipping (`proposed`).
