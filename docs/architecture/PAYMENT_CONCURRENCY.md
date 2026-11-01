# Payment concurrency, idempotency & F3 (TH-103)

## States (oracle ground truth = `payments.state`)

`PENDING` · `CAPTURED` · `FAILED` · `UNKNOWN` · `REFUNDED`

- Timeout / lost response ≠ `FAILED`.
- Refund only from `CAPTURED`.
- Resolve: confirmed capture → `CAPTURED`; authoritative non-capture → `FAILED`; inconclusive keeps `UNKNOWN`/`PENDING`.

## Transaction strategy

| Operation | Locks |
| --- | --- |
| charge / refund | `pg_advisory_xact_lock(hashtext(idempotency_key))` then idempotency `FOR UPDATE` |
| resolve | `pg_advisory_xact_lock` on `payment:{id}` then payment row `FOR UPDATE` |

Unique `order_id` prevents duplicate payment rows. Idempotency row + payment mutation + `operation_log` commit together.

## F3 deterministic simulation

**Not** a production control API. Enabled only when `thesis.payment.fault-injection-enabled=true` (profile `test` / explicit config).

Header: `X-Thesis-Fault`

| Value | Persist (`payments.state`) | Charge HTTP response |
| --- | --- | --- |
| _(none)_ / `CHARGE_CAPTURE` | `CAPTURED` | `CAPTURED` |
| `CHARGE_FAIL` | `FAILED` | `FAILED` |
| `CHARGE_UNKNOWN` | `UNKNOWN` | `UNKNOWN` |
| **`F3_CAPTURE_LOST_RESPONSE`** | **`CAPTURED`** | **`UNKNOWN`** |

F3 sequence:

1. Charge with `F3_CAPTURE_LOST_RESPONSE` → DB `CAPTURED`, client sees `UNKNOWN`.
2. `operation_log` records `persistedState` vs `observedResponse`.
3. `GET /payments/{id}` (oracle path) shows `CAPTURED`.
4. `POST .../resolve` returns snapshot `CAPTURED` (discovery).
5. Refund without confirmed capture (caller only saw UNKNOWN and never resolved/GET) is refused if they somehow had PENDING/UNKNOWN — refund requires CAPTURED in DB; after F3 DB is CAPTURED so refund *is* allowed once capture is known. Timeout alone never authorizes treating payment as FAILED.

Independent of variants A/C — same Payment API.
