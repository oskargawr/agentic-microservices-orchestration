# I7 enforcement map (TH-401-A audit)

**Invariant I7 (frozen TH-011 §4):** *Replaying the same idempotency key does not double-hold, double-charge, double-ship, or double-consume.*

This document maps I7 to **actual enforcement**. It does **not** change the frozen definition. Operation logs are **not** authoritative for oracle classification.

| Effect | DB constraint (Source fact) | Service / idempotency (Source fact) | Observable by Oracle at TERMINAL |
| --- | --- | --- | --- |
| Duplicate **HELD** reserve same order | Partial unique `uq_reservations_order_held` on `(order_id) WHERE state=HELD` | Reserve inserts HELD; conflict → business error; idempotent key replay returns same id | **Yes:** `heldCount > 1` → I2 / multiple rows → `INVALID` |
| Duplicate reservation rows (re-reserve after RELEASED) | Allowed by schema (unique only for HELD) | New reserve = new id | **Yes (MVP policy):** `reservations.size() > 1` → `INVALID` (`MULTIPLE_RESERVATIONS`) |
| Double **charge** / two payments per order | Unique `uq_payments_order` on `order_id` | Charge inserts one row; idempotency replay | **Indirect:** at most one payment row loadable per `orderId`; Oracle does not replay keys |
| Double **arrange** / two shipments | Unique `uq_shipments_order` on `order_id` | Arrange inserts one row; idempotency replay | **Indirect:** at most one shipment row per `orderId` |
| Double **consume** | No second CONSUMED row; state machine refuses non-HELD consume; idempotent consume no-ops if already CONSUMED | `InventoryService.consume` | **Limited:** Oracle sees one reservation state; cannot detect “consume called twice” if both were no-ops — by design OK |
| Idempotent **replay** same key | `idempotency_records` PK on key | Services lock key, replay body | **Not classified by Oracle:** would require replaying keys / comparing request fingerprints. Covered by service ITs (`idempotentReserve*`, `idempotentCharge*`, `idempotentArrange*`) |

## Oracle coverage statement (honest)

- Oracle **does** surface terminal duplicate reservation / multi-HELD as `INVALID`.
- Oracle **does not** independently re-execute idempotent replays or treat `operation_log` as proof of I7.
- Payment/shipment uniqueness is **assumed from schema + service tests**; a corrupted DB bypassing constraints would be an infra/technical exclusion, not an RQ1 oracle claim.
- Absolute stock `onHand` decrement vs pre-run baseline is **out of band** for the terminal snapshot (separate from I7).

## Related service-level evidence

- `InventoryIntegrationTest` — idempotent reserve / conflict
- `PaymentIntegrationTest` — idempotent charge / conflict
- `ShippingIntegrationTest` — idempotent arrange / conflict
