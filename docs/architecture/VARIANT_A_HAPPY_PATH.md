# Variant A — Temporal happy path (TH-200)

Deterministic Temporal workflow for F0 (no faults, no compensation).

## Architecture

```text
OrderFulfillmentWorkflow (deterministic)
  └─ OrderFulfillmentActivities (I/O)
       ├─ POST inventory /reservations
       ├─ POST payment /charges
       ├─ POST shipping /shipments/arrange
       └─ POST inventory /reservations/{id}/consume
```

Independent oracle reads (outside workflow success alone):

- `GET /reservations/{id}` → `CONSUMED`
- `GET /payments/{id}` → `CAPTURED`
- `GET /shipments/{id}` → `ARRANGED`

## Activity options (rationale)

| Setting | Value | Why |
| --- | --- | --- |
| Start-to-close | 10s | Local HTTP to Spring services; above p99, below protocol observe window |
| Schedule-to-close | 60s | Allows a few retries without approaching `T_run_max` |
| Retry initial / max interval | 200ms / 2s | Transient connection blips only |
| Max attempts | 5 | Bounded; happy path must not mask persistent outages |
| Non-retryable | `BUSINESS` ApplicationFailure | 4xx / wrong business state must not spin |

No saga / F3 / LLM in this task.

## Consume-after-arrange (C-101-1)

Workflow calls `consume` **only after** `arrange` returns `ARRANGED`. Activities do not reorder steps.

## MVP order constants (assumption)

Protocol requires fixed `qty` and `amount` without naming the amount. Tests use:

- `sku = SKU-MVP-1`, `qty = 1`
- `amount = Money.plnMinor(2599)` (PLN)

Change only via input record; not a new business rule.
