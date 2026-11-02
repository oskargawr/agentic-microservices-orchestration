# Variant A — Retries and Timeouts (TH-202)

| Field | Value |
| --- | --- |
| **Status** | Design / engineering documentation (TH-202) |
| **Decisions** | D-202-1…D-202-5 (Owner-approved 2026-11-02) |
| **Code** | `ActivityPolicies`, `HttpOrderFulfillmentActivities`, `OrderFulfillmentWorkflowImpl` |
| **Process overview** | [`VARIANT_A_HAPPY_PATH.md`](VARIANT_A_HAPPY_PATH.md) |

**Labels used below:** Source fact · Assumption · Design rationale · Limitation

These ActivityOptions values are **current engineering defaults** (D-202-1). They are **not** derived from experimental protocol constants and are **not** claimed to be experimentally optimized.

---

## 1. Exact ActivityOptions (Source fact)

Location: `orchestration/variant-a/src/main/java/.../ActivityPolicies.java`

| Setting | Forward (`defaultOptions`) | Compensation (`compensationOptions`) |
| --- | --- | --- |
| Start-to-close | 10s | 10s |
| Schedule-to-close | 60s | 90s |
| Initial retry interval | 200ms | 200ms |
| Maximum retry interval | 2s | 2s |
| Backoff coefficient | 2.0 | 2.0 |
| Maximum attempts | 5 | 5 |
| `doNotRetry` types | `BUSINESS`, `COMPENSATION_INCOMPLETE` | same |

Wiring (Source fact):

- Forward stub: `OrderFulfillmentWorkflowImpl` → `ActivityPolicies.defaultOptions()`
- Compensation stub: same class → `ActivityPolicies.compensationOptions()`

**Design rationale:** Compensation gets a longer schedule-to-close (90s vs 60s) so cancel → refund → release can still finish after forward work already consumed schedule budget. Numerics otherwise match forward policy for predictable local/CI behavior.

---

## 2. Forward vs compensation retry policies (Source fact)

Both policies share the same retry curve and non-retryable application types. Only **schedule-to-close** differs (60s vs 90s). Compensation activities still retry transient HTTP failures; they never spin on `BUSINESS` / `COMPENSATION_INCOMPLETE`.

---

## 3. Retryable vs non-retryable classification (Source fact + Assumption)

### Verified in code (Source fact)

`HttpOrderFulfillmentActivities.toFailure(RestClientResponseException)`:

| HTTP status | ApplicationFailure | Temporal retry |
| --- | --- | --- |
| 4xx | `newNonRetryableFailure(..., BUSINESS)` | **No** |
| 5xx | `newFailure(..., "HTTP_"+status)` | **Yes** (until max attempts / schedule-to-close) |

Workflow-level business guards (`require()`, charge `FAILED`, unresolved UNKNOWN → AMBIGUOUS) throw non-retryable `BUSINESS`. Incomplete compensation throws non-retryable `COMPENSATION_INCOMPLETE`.

### Not explicitly classified (Assumption / Limitation — D-202-2)

Failures that are **not** `RestClientResponseException` (e.g. connection reset, DNS, client I/O) are **not** wrapped by `toFailure`. **Assumption:** Temporal treats them as retryable activity failures by default. TH-202 does **not** change this classification (D-202-2).

---

## 4. Temporal retries vs payment resolve loop (Source fact)

| Mechanism | Bound | Purpose |
| --- | --- | --- |
| Temporal activity retries | Max **5** attempts per activity invocation (policy) | Transient technical failures |
| Workflow UNKNOWN resolve loop | Default **3** (`OrderFulfillmentInput.DEFAULT_RESOLVE_ATTEMPTS`) | Business probing of unresolved payment |

These bounds are **independent**. A single `resolvePayment` activity call may itself be Temporal-retried on 5xx/timeout; the workflow still performs at most `resolveAttemptLimit()` successful resolve *steps* in the UNKNOWN loop.

---

## 5. Idempotency vs duplicate mutating calls (Source fact)

Mutating activities send stable `Idempotency-Key` values from `IdempotencyKeys` (TH-101.1 / D-015):

| Activity | Key pattern |
| --- | --- |
| reserve / charge / arrange | `{orderId}:…` |
| consume / release | `{reservationId}:…` |
| refund | `{paymentId}:refund` |
| cancelShipment | `{shipmentId}:cancel` |

Same key + compatible payload → service replays prior response; conflict → 409 → non-retryable `BUSINESS`.

`resolvePayment` does **not** send an idempotency header. Payment `resolve` is re-entrant under a payment-id lock (Source fact). Extra Temporal retries may add `operation_log` rows but must not invent contradictory terminal states.

---

## 6. Timeout / retry exhaustion and Saga compensation (Source fact)

When a forward activity fails after Temporal retries (or non-retryable `BUSINESS`), `OrderFulfillmentWorkflowImpl` runs compensation in `Workflow.newDetachedCancellationScope` (D-201 / U-201-3):

1. If shipment `ARRANGED` → cancel  
2. If payment `CAPTURED` (authoritative GET) → refund — **never** refund UNKNOWN/PENDING/FAILED  
3. If reservation `HELD` → release — **never** release CONSUMED  

Outcomes:

- Required compensation completes + prior AMBIGUOUS pending → `AMBIGUOUS`
- Required compensation completes otherwise → `COMPENSATED_FAILURE`
- Compensation incomplete → workflow fails with `COMPENSATION_INCOMPLETE` (not reported as compensated success)

**Critical (D-011 / D-202):** Activity timeout or retry exhaustion must **not** be treated as payment `FAILED`. Compensation decisions use authoritative snapshots when resource IDs are known.

---

## 7. Activity timeouts vs protocol `T_observe` / `T_run_max` (Source fact + Design rationale)

| Bound | Where | Meaning |
| --- | --- | --- |
| Activity start-/schedule-to-close | Temporal `ActivityOptions` | Per-activity execution / retry budget |
| `T_observe = 30s` | Experimental protocol / harness (D-012) | Post-workflow observation window |
| `T_run_max = 120s` | Experimental protocol / harness (D-012) | Hard wall-clock run deadline |

**D-202-4:** TH-202 does **not** set a Temporal workflow execution timeout. Protocol times remain harness/oracle concerns and must not be conflated with `ActivityPolicies` numbers.

---

## 8. Explicit limitations

| Limitation | Notes |
| --- | --- |
| Lost response after commit **without returned resource ID** | Deferred (D-202-3) to a separate F3-related task. No orderId payment discovery in TH-202. If charge/reserve times out after side effect but before return, workflow state may lack IDs → cannot refund/release by id. **Do not** model this as a clean `COMPENSATED_FAILURE` in TH-202 tests. |
| No workflow-level execution timeout | D-202-4; harness owns `T_run_max`. |
| No explicit RestClient connect/read timeout | `HttpOrderFulfillmentActivities` builds default `RestClient` (Source fact). |
| Transport exceptions not wrapped | D-202-2; classification remains Assumption for non-HTTP failures. |
| Values not experimentally optimized | Engineering defaults only (D-202-1). |

---

## 9. Tests (TH-202)

| ID | Coverage |
| --- | --- |
| T-202-1 | Exact forward/compensation option values + `doNotRetry` |
| T-202-2 | Retryable failure then success (Temporal retries observed) |
| T-202-3 | `BUSINESS` → single attempt + compensation semantics |
| T-202-4 | Retry exhaustion with known IDs → compensation; payment not marked FAILED |
| T-202-5 | UNKNOWN resolve loop ≤ `resolveAttemptLimit`, distinct from Temporal retries |
| T-202-6 | Stable `IdempotencyKeys` patterns for mutating activities |
