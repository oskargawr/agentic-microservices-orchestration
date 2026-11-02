# TH-400 S0 — Technical verification report

| Field | Value |
| --- | --- |
| **Date** | 2026-11-02 |
| **Mode** | READ-ONLY code inspection + documentation update |
| **Inspected tree** | `docs/th-400-s0-fault-profile-spec` / `origin/main` lineage |
| **Normative outcome** | Bound into `TH_400_FAULT_PROFILE_SPEC.md` §4.4, §5.3.1, §7 |

Labels: **Source fact** · **Specified (S0)** · **Owner decision**

---

## A. F3-Unresolved retry budget

### Source facts

| Mechanism | Finding | Evidence |
| --- | --- | --- |
| Logical resolve loop | `for (i < input.resolveAttemptLimit())` calling `resolvePayment` | `OrderFulfillmentWorkflowImpl.handleUnresolvedPayment` |
| Default logical limit | **3** (`OrderFulfillmentInput.DEFAULT_RESOLVE_ATTEMPTS`) | `OrderFulfillmentInput.java` |
| What the limit counts | **Logical iterations only**, not HTTP calls | Loop structure |
| Per-activity Temporal retries | Forward activities: **maxAttempts = 5**, backoff 200ms→2s, coeff 2.0 | `ActivityPolicies.defaultOptions` · D-202-1 |
| 503 classification | Status ≥500 → retryable type `HTTP_503` (not in doNotRetry) | `HttpOrderFulfillmentActivities.toFailure` |
| 4xx classification | Non-retryable `BUSINESS` | same |
| Extra HTTP client retries | **None** observed (one RestClient invoke per activity attempt) | `HttpOrderFulfillmentActivities` |
| `getPayment` after loop | Only if `snap == null` after loop; **no fault-hint argument today** | workflow + activities API |

### Implication

Worst-case HTTP resolve attempts ≈ `resolveAttemptLimit × 5` (e.g. **15** at defaults) **before** any `getPayment`. A budget of “3 opaque HTTP calls” would be exhausted inside the **first** logical iteration’s Temporal retries; a later attempt could return 200 CAPTURED and convert the cell into **F3-Recovered**.

### Normative S0 decision (**Specified**)

- Prefer **header-presence opacity for the whole trial** (no silent nested multiplier).  
- Else document an explicit counter ≥ `resolveAttemptLimit × forwardMaxAttempts` (+ getPayment term), keyed by payment/order id.  
- Forbid un-hinted payment-state GET on this cell’s unresolved path.

Owner **OQ-F3U-2** (HTTP 503) remains compatible with Temporal retries **because** retries keep sending the opacity header under the preferred model.

---

## B. Trial isolation

### Source facts

| Element | Finding |
| --- | --- |
| Arm request | `trialId`, `orderId`, `faultProfileId`, `variant` (`FaultArmRequest`) |
| Trial identity | Unique `orderId` / `workflowId` per `TrialSpec` |
| Concurrency | Serial trials + env lock (**D-R7**) |
| Current injection | Payment honors `X-Thesis-Fault` when `fault-injection-enabled`; request-scoped |
| Runner F0 controller | Arms keyed by `trialId` in-memory map |

### Gaps if naïve

- A **process-global opaque counter** without `paymentId`/`orderId` keys could cross-consume budgets.  
- Leaving a header hint on a shared worker stub across trials could leak (mitigated by per-trial workflow input + disarm + verify).

### Normative S0 mitigations (**Specified**)

- Attach fault headers only on the armed trial’s outbound calls.  
- Prefer header-presence (no shared counter).  
- Record correlation fields in `effectiveConfig`.  
- No new production API header without Owner approval.  
- Verify-before-start + disarm per `trialId`.

---

## C. F2 persistence / atomicity

### Source facts

| Element | Finding |
| --- | --- |
| Happy-path arrange | Insert shipment + idempotency success body + op-log in one `@Transactional` method | `ShippingService.arrange` |
| Idempotency contract | Same key+fingerprint → replay; same key+different fingerprint → `IDEMPOTENCY_KEY_CONFLICT` | `IdempotencyRules` |
| Failure replay today | Success-oriented JSON replay only — **no** F2 failure row yet | shipping persistence |

### Normative S0 requirements (**Owner OQ-F2-1** + **Specified**)

Single TX on F2 reject:

1. No shipment row.  
2. Idempotency **failure** outcome committed (`ARRANGE_REJECTED`, HTTP 409).  
3. Operation log entry committed.  

Same key+fp → replay failure (including after disarm). Same key+diff fp → `IDEMPOTENCY_KEY_CONFLICT`. Happy-path unchanged.

Crash before commit: no shipment; safe to reject again. Crash after commit: durable failure replay prevents late create.

---

## Summary

| Topic | Result |
| --- | --- |
| A Retry budget | Logical limit ≠ HTTP limit; S0 forbids silent under-budgeting |
| B Isolation | Header-per-trial + serial lock sufficient if no unkeyed global counter; documented |
| C F2 atomicity | Normative TX + idempotent failure replay specified; impl in S2 |
