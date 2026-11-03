# 01 — Research context

## Problem

The thesis investigates **reliability of agentic / microservice orchestration** for a multi-step business process when services fail, responses are lost, or outcomes are ambiguous. The MVP comparison target is **Variant A vs Variant C** (D-009); **only Variant A is implemented**.

## Distributed process under study

**Order fulfillment saga** (Variant A):

1. Inventory **reserve** → `HELD`  
2. Payment **charge** → `CAPTURED` (or resolve from `UNKNOWN`)  
3. Shipping **arrange** → `ARRANGED`  
4. Inventory **consume**  
5. On failure: compensation **cancel** shipment (if needed) → **refund** CAPTURED payment → **release** HELD reservation  

Source: `orchestration/variant-a/.../OrderFulfillmentWorkflowImpl.java` → `fulfill()`.

## Why microservices

Separate Inventory, Payment, Shipping schemas and HTTP APIs allow:

- Independent fault injection per service  
- Independent Oracle JDBC reads of business state  
- Fair comparison later between orchestration styles on **identical** contracts (`:contracts`)

## Why orchestration reliability matters

Side effects (stock hold, money capture, shipment) are not a single ACID transaction across services. Without durable orchestration, retries create duplicates; without compensation, partial success leaves inconsistent end-states. The Oracle measures **business end-state**, not “workflow said OK”.

## What Temporal contributes (Variant A)

- Durable workflow execution and history  
- Activity retries with typed non-retryable business failures (`ActivityPolicies`)  
- Clear terminal Temporal statuses (`COMPLETED`, `TERMINATED`, …) separate from harness status  

Task queue: `OrderFulfillmentWorkflow.TASK_QUEUE` = `variant-a-order-fulfillment`.

## Failure scenarios investigated (implemented profiles)

Canonical IDs (`CanonicalFaultProfiles.ALL`): **F0**, **F1-P**, **F2**, **F3-Recovered**, **F3-Unresolved**.  
`F1` and `F3` alone are **rejected** as profile IDs.

## What the Oracle measures

Independent JDBC snapshot → `OutcomeClassifier.classify()` → one of four labels:

`CONFIRMED_SUCCESS` | `CONFIRMED_FAILURE` | `AMBIGUOUS` | `INVALID`

(`contracts/.../BusinessOutcome.java`). Not derived from LLM text or Temporal status alone (TH-011 / D-011).

## What the experimental harness does

`TrialRunner.run()`: ENV lock / JDBC allowlist → `StateReset` → fault arm/verify → start workflow → await / R6 terminate → quiesce → R14 observe → Oracle **once** → immutable `TrialRecord` → disarm.

## Implemented vs planned

| Item | Status |
| --- | --- |
| Variant A Temporal saga + workers + console | **implemented / merged** |
| Shared contracts + three Spring services | **implemented / merged** |
| Independent Oracle + classifier | **implemented / merged** |
| Runner F0–F3 matrix + FileResultStore | **implemented / merged** (TH-400 S0–S4-C) |
| G5 opt-in live Temporal lab code | **merged / CI-verified** (PR #16); **not** live-tested |
| Variant C (LLM activity) | **not implemented** (unauthorized) |
| S4-D | **deferred / unauthorized** |
| EXP-* campaigns | **none** |
