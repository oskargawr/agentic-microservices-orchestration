# HANDOFF_TO_CHATGPT

**Date:** 2026-11-01  
**Task:** TH-200 — Variant A Temporal happy path (+ TH-104 closure)  
**Prepared by:** Cursor Agent  
**MODE:** IMPLEMENTATION  
**Branch:** `feature/th-200-variant-a-happy-path`

---

## TH-104 closure

PR #3 **merged** to `main` (`45849ab`). CI run `41324579450`: Postgres healthy; shipping/payment/inventory HikariPool + `:test` tasks succeeded (not skipped); local re-verify 7/7 + 13/13.

---

## TH-200 — Architecture

Module `:orchestration:variant-a`:

- `OrderFulfillmentWorkflow` / `OrderFulfillmentWorkflowImpl` — deterministic steps only
- `OrderFulfillmentActivities` / `HttpOrderFulfillmentActivities` — HTTP to inventory/payment/shipping
- Docs: `docs/architecture/VARIANT_A_HAPPY_PATH.md`

Sequence: **reserve → charge → arrange → consume** (C-101-1).

## Activity contracts / retries

| | |
| --- | --- |
| Start-to-close | 10s |
| Schedule-to-close | 60s |
| Retry | 5 attempts, 200ms→2s backoff |
| Non-retryable | `BUSINESS` (4xx / wrong state) |

## Tests

| Test | Result |
| --- | --- |
| `OrderFulfillmentWorkflowOrderTest` | 1/1 — order reserve/charge/arrange/consume |
| `OrderFulfillmentHappyPathIT` | 1/1 — Temporal test env + 3 bootJar processes + Postgres; GET snapshots CONSUMED/CAPTURED/ARRANGED; stock 99/0 |
| Skipped | **0** (when Postgres up) |

```bash
./gradlew :orchestration:variant-a:test build
```

## Final business states (IT)

- reservation = `CONSUMED`
- payment = `CAPTURED`
- shipment = `ARRANGED`
- Workflow result consistent with independent GETs

## Unresolved

- Protocol says fixed amount without numeric value — tests use `Money.plnMinor(2599)` (documented assumption, not a new invariant).

## Commits / PR

| **HEAD** | *(set after commit)* |
| **PR** | *(set after open)* |

**Next proposed:** TH-201 Saga compensations A.
