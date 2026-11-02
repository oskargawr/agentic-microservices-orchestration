# Variant A — Temporal workflow (TH-200 / TH-201)

Deterministic Temporal workflow for order fulfillment with saga compensation.

## Architecture

```text
OrderFulfillmentWorkflow (deterministic)
  └─ OrderFulfillmentActivities (I/O)
       Forward:
       ├─ POST inventory /reservations
       ├─ POST payment /payments/charge  (+ optional X-Thesis-Fault)
       ├─ POST payment /payments/{id}/resolve  (bounded, UNKNOWN only)
       ├─ POST shipping /shipments/arrange
       └─ POST inventory /reservations/{id}/consume
       Compensation (detached cancellation scope):
       ├─ POST shipping /shipments/{id}/cancel   (if ARRANGED)
       ├─ POST payment /payments/{id}/refund     (if CAPTURED only)
       └─ POST inventory /reservations/{id}/release (if HELD only)
```

Independent oracle reads (outside workflow success alone):

- `GET /reservations/{id}`
- `GET /payments/{id}`
- `GET /shipments/{id}`
- plus `operation_log` ordering where required

Temporal workflow status / result alone is **not** business success.

## Forward process

1. Reserve → expect `HELD`
2. Charge → expect `CAPTURED` (or handle `FAILED` / `UNKNOWN`)
3. Arrange → expect `ARRANGED`
4. Consume → expect `CONSUMED` (C-101-1: only after confirmed arrange)

## Compensation order (TH-011 / D-201)

Always evaluate **authoritative snapshots** before each step:

1. If shipment `ARRANGED` → cancel → `CANCELLED`
2. If payment `CAPTURED` → refund → `REFUNDED` (never refund `UNKNOWN` / `PENDING` / `FAILED`)
3. If reservation `HELD` → release → `RELEASED` (never release `CONSUMED`)

Skip steps whose preconditions are authoritatively false.

## UNKNOWN payment (U-201-2)

- Do not arrange while unresolved.
- Bounded resolve attempts (default **3**, overridable via `OrderFulfillmentInput.maxPaymentResolveAttempts`).
  This bound is **distinct** from Temporal activity retries.
- `RESOLVE` / charge fault hints are test/experiment controls (`X-Thesis-Fault`), not protocol constants.
- If resolve confirms `CAPTURED` → continue forward.
- If resolve confirms `FAILED` → compensate (release HELD) → `COMPENSATED_FAILURE`.
- If still `UNKNOWN` → no arrange, no refund; release HELD if safe; outcome **`AMBIGUOUS`**.
  Releasing inventory does **not** prove payment was not captured.

## Structured workflow outcomes (U-201-5)

| Outcome | Meaning |
| --- | --- |
| `SUCCESS` | CONSUMED + CAPTURED + ARRANGED |
| `COMPENSATED_FAILURE` | Forward failed in a confirmed way **and** every required compensation completed |
| `AMBIGUOUS` | Payment unresolved after bounded resolve |

Incomplete compensation / unhandled technical errors → non-retryable `ApplicationFailure` type `COMPENSATION_INCOMPLETE` (workflow fails). **Not** reported as `COMPENSATED_FAILURE`.

## Temporal cancellation (U-201-3)

Forward work runs in a cancellable scope. Compensation runs in `Workflow.newDetachedCancellationScope` so required compensation can still execute if the main scope is cancelled. Cancellation is then rethrown after compensation attempt.

## Activity options

| Setting | Forward | Compensation |
| --- | --- | --- |
| Start-to-close | 10s | 10s |
| Schedule-to-close | 60s | 90s |
| Max attempts | 5 | 5 |
| Non-retryable | `BUSINESS`, `COMPENSATION_INCOMPLETE` | same |

## Idempotency

All mutating calls use existing `Idempotency-Key` helpers (`IdempotencyKeys`). Activity retries must not double-apply business effects.

## State reconciliation

After ambiguous HTTP / timeouts, inspect GET snapshots before retry, continue, or compensate. Never blindly compensate from a failed HTTP assumption alone.

## Test amount

Tests use `Money.plnMinor(2599)` as an **explicit local test input** — not an approved protocol constant.

## Known limitations

- Console (TH-205) observation of new outcomes is optional / out of mandatory TH-201 scope.
- Full F0–F3 experiment fault profiles remain TH-400.
- Exact numeric resolve bound may be tuned later without changing AMBIGUOUS semantics.
