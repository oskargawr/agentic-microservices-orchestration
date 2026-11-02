# TH-201 implementation outline

**Branch:** `feature/th-201-variant-a-saga-compensation` (from `origin/main` @ `1137fd1`)  
**Date:** 2026-11-02  
**Status:** in progress

## Preserved dirty work

Unrelated console-ui / CORS WIP was **stashed** (`stash^{/preserve dirty tree before th-201 branch}`) and not discarded.

## Plan

1. Extend `OrderFulfillmentActivities` + `HttpOrderFulfillmentActivities`: cancel, refund, release, resolve; charge may pass `X-Thesis-Fault`.
2. Add `BusinessOutcome` + evolve `OrderFulfillmentResult` (nullable shipment; compensation diagnostics).
3. Rewrite `OrderFulfillmentWorkflowImpl` with saga try/catch + `newDetachedCancellationScope` for compensation; bounded payment resolve (default 3, distinct from activity retries).
4. Unit tests (ordering / outcomes) + IT `OrderFulfillmentCompensationIT` (Postgres + TestWorkflowEnvironment + bootJars; payment fault injection on).
5. Update `VARIANT_A_HAPPY_PATH.md` → compensation section (or rename doc).
6. Build + PR (no merge).

## Defaults (X-201-2)

`maxPaymentResolveAttempts = 3` when input omits bound.
