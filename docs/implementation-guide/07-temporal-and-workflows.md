# 07 — Temporal and workflows

## Workflow API

- Interface: `OrderFulfillmentWorkflow` — `fulfill(OrderFulfillmentInput) → OrderFulfillmentResult`  
- Impl: `OrderFulfillmentWorkflowImpl`  
- Activities interface: `OrderFulfillmentActivities`  
- Activities HTTP impl: `HttpOrderFulfillmentActivities` (attaches `X-Thesis-Fault` when hints present)  
- Queue: `variant-a-order-fulfillment`  
- Worker registration: `VariantAWorkers` / `VariantAWorkerApplication` / `WorkerRuntime`
## Forward path (as implemented)

1. `reserve` → require `HELD`  
2. `charge` (optional fault hint) → handle `FAILED` / `UNKNOWN` / `CAPTURED`  
3. If `UNKNOWN`: bounded `resolve` loop (`lostIdRecovery` / resolve activities)  
4. `arrange` (optional fault hint) → require `ARRANGED`  
5. `consume` reservation  

## Compensation (saga)

On forward failure after side effects: cancel shipment if needed → `refund` if CAPTURED → `release` if HELD. Incomplete compensation raises `COMPENSATION_INCOMPLETE` (non-retryable type).

## Activity policies (`ActivityPolicies`)

| Policy | StartToClose | ScheduleToClose | Max attempts | Notes |
| --- | --- | --- | --- | --- |
| `defaultOptions` | 10s | 60s | 5 | DoNotRetry BUSINESS, COMPENSATION_INCOMPLETE |
| `compensationOptions` | 10s | 90s | 5 | Same non-retryable types |
| `lostIdRecoveryOptions` | 10s | 30s | **1** | Single recovery execution (D-208) |

## Why Temporal instead of pure REST orchestration

Durable history, automatic retries, crash recovery of workflow code, and a first-class distinction between **workflow terminal status** and **business outcome** (Oracle). A plain REST orchestrator process is a single point of failure for in-flight sagas unless it reimplements that durability.

## TestWorkflowEnvironment vs real Temporal

| Level | What | Proves | Does not prove |
| --- | --- | --- | --- |
| CompIT `TestWorkflowEnvironment` | In-process Temporal test server | Saga logic, compensation, retries | Production poller exclusivity, real timing of G5-3 |
| Console E2E / G5 live | Real Temporal + real worker | Networked server behavior | EXP-* statistical claims |

## Console

`orchestration:console` — operator-triggered starts; gated `ConsoleRealTemporalE2EIT`.
