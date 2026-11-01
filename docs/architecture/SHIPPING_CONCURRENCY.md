# Shipping concurrency & idempotency (TH-104)

## States

`NONE` (conceptual initial; no row) → `ARRANGED` (row created on arrange) → `CANCELLED`.

## Transaction strategy

| Operation | Locks |
| --- | --- |
| arrange | `pg_advisory_xact_lock(hashtext(idempotency_key))` + unique `order_id` |
| cancel | advisory lock on key + shipment row `FOR UPDATE` |

Idempotency record + shipment mutation + `operation_log` commit together.

## Guarantees

- Same Idempotency-Key + same payload → replay; conflict on payload mismatch.
- Concurrent arrange with same key → single shipment.
- Concurrent cancel with same key → single `CANCELLED` transition / effect.
- Cancel of already `CANCELLED` is idempotent (200, no extra business mutation beyond first log).
