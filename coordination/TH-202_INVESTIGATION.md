# TH-202 — Investigation & proposed plan

**Date:** 2026-11-02  
**Branch inspected:** `main` @ `9385fdf`  
**Mode:** RESEARCH only — **no application code changes**  
**Labels:** Source fact · Synthesis · Hypothesis · Open question

---

## 1. Current retry / timeout configuration (Source fact)

Canonical code: `orchestration/variant-a/.../ActivityPolicies.java`.  
Also summarized in `docs/architecture/VARIANT_A_HAPPY_PATH.md` §Activity options.

| Setting | Forward (`defaultOptions`) | Compensation (`compensationOptions`) |
| --- | --- | --- |
| Start-to-close | **10s** | **10s** |
| Schedule-to-close | **60s** | **90s** |
| Initial retry interval | 200ms | 200ms |
| Max retry interval | 2s | 2s |
| Backoff coefficient | 2.0 | 2.0 |
| Maximum attempts | **5** | **5** |
| `doNotRetry` types | `BUSINESS`, `COMPENSATION_INCOMPLETE` | same |

Wiring (Source fact):

- Forward stub: `OrderFulfillmentWorkflowImpl` → `ActivityPolicies.defaultOptions()`
- Compensation stub: same class → `ActivityPolicies.compensationOptions()`
- HTTP mapping: `HttpOrderFulfillmentActivities.toFailure`
  - HTTP **4xx** → `ApplicationFailure.newNonRetryableFailure(..., BUSINESS)`
  - HTTP **5xx** → retryable `ApplicationFailure.newFailure(..., "HTTP_"+status)`
- Workflow / client options: console and tests set task queue + workflowId only — **no** `WorkflowExecutionTimeout` / `WorkflowRunTimeout` set in inspected starters.

Distinct from Temporal retries (Source fact):

- Payment UNKNOWN resolve loop: `OrderFulfillmentInput.DEFAULT_RESOLVE_ATTEMPTS = 3` (`resolveAttemptLimit()`), documented as **independent** of activity retries.

---

## 2. What is retried vs not (Source fact + Synthesis)

| Failure class | Retried by Temporal? | Evidence |
| --- | --- | --- |
| Application type `BUSINESS` (4xx, workflow `require()`, FAILED/AMBIGUOUS throws) | **No** | `doNotRetry` + `newNonRetryableFailure` |
| Type `COMPENSATION_INCOMPLETE` | **No** | `doNotRetry` |
| HTTP 5xx typed as `HTTP_5xx` | **Yes** (until max attempts / schedule-to-close) | `newFailure` (retryable) |
| Activity start-to-close timeout / heartbeat-less hang | **Yes** as Temporal timeout failures (until schedule-to-close / max attempts) | Temporal default behavior + options above |
| Non-`RestClientResponseException` transport errors (e.g. connection reset) | **Assumed yes** (untyped / runtime → Temporal retryable) | **Assumption** — not explicitly wrapped in `toFailure` |

Business path throws that skip Temporal retries (Source fact): charge `FAILED`, unresolved UNKNOWN → `AMBIGUOUS`, snapshot `require()` mismatches — all `BUSINESS` non-retryable, then saga compensation.

---

## 3. Duplicate operations & idempotency (Source fact + Synthesis)

Mutating activities send fixed `Idempotency-Key` via `IdempotencyKeys` (Source fact):

| Activity | Key pattern |
| --- | --- |
| reserve / charge / arrange | `{orderId}:…` |
| consume / release | `{reservationId}:…` |
| refund | `{paymentId}:refund` |
| cancelShipment | `{shipmentId}:cancel` |

Services persist idempotency records (TH-101.1 / D-015) — same key + compatible payload replays prior response; conflict → 409 → mapped to non-retryable `BUSINESS`.

**Synthesis:** Temporal activity retries of mutating calls are intended to be **business-safe** because keys are stable per logical operation.

**Source fact gap:** `resolvePayment` does **not** send `Idempotency-Key`. Payment `resolve` locks by `paymentId` and is re-entrant (terminal states short-circuit). Bounded workflow loop may call resolve up to 3 times **plus** Temporal may retry each call on 5xx/timeout — safe for state machine, may inflate `operation_log` resolve rows (**Synthesis**).

GETs used in compensation have no idempotency header (read-only) — retries are side-effect free (**Synthesis**).

---

## 4. Timeout exhaustion → workflow failure → compensation (Source fact + Synthesis)

On forward `ActivityFailure` / `ApplicationFailure`, workflow runs compensation in `newDetachedCancellationScope` (D-201 / U-201-3).

If compensation completes:

- `pendingOutcome == AMBIGUOUS` → return `AMBIGUOUS`
- else → return `COMPENSATED_FAILURE` (default when `pendingOutcome` null)

If compensation throws → non-retryable `COMPENSATION_INCOMPLETE` (workflow fails; not reported as compensated success).

**Synthesis — interaction with protocol “timeout ≠ failure” (D-011 / D-012):**

- Protocol/oracle: client timeout / lost response must **not** be treated as confirmed payment failure; UNKNOWN / AMBIGUOUS at harness TERMINAL.
- Current workflow: a **technical** activity timeout after retries is an `ActivityFailure` → compensation → typically **`COMPENSATED_FAILURE`** if no `pendingOutcome` was set.
- That is consistent **only if** compensation preconditions are evaluated from authoritative GETs (cancel/refund/release skips) — which the code does.
- **Reliability gap (Source fact + Synthesis):** if `charge` (or `reserve`) times out **after** the service committed but **before** the activity returns IDs, workflow state may lack `paymentId` / `reservationId` → compensation cannot refund/release that resource by ID. There is **no** orderId-based payment lookup activity. Protocol F-PAY-AMB / F3 path today is primarily exercised via **explicit UNKNOWN responses** / fault headers, not via true lost-response-without-return-value.

---

## 5. Consistency with D-201 and protocol (Synthesis)

| Decision | Verdict |
| --- | --- |
| U-201-2 UNKNOWN bound + AMBIGUOUS + no refund unless CAPTURED | **Consistent** with current workflow |
| U-201-3 detached compensation | **Consistent** |
| U-201-5 COMPENSATION_INCOMPLETE ≠ COMPENSATED_FAILURE | **Consistent** |
| D-012 OQ-2 timeout ≠ confirmed failure; F3 mandatory in fault set | **Partially aligned:** UNKNOWN/AMBIGUOUS path OK; true lost-response-without-ID **not implemented/tested** as workflow behavior |
| D-012 OQ-3 `T_observe=30s`, `T_run_max=120s` | **Orthogonal:** harness observation bounds — **not** encoded as Temporal workflow/activity timeouts. Activity schedule-to-close 60s/90s fit under 120s wall clock **by assumption**, but are not derived from protocol constants in code |
| Activity option numeric values | Documented in VARIANT_A doc; **no written rationale** linking them to protocol T_* (**gap**) |

---

## 6. Existing tests & coverage gaps (Source fact)

Present (TH-200/201):

- Unit: happy path + T-201-1…6 compensation / UNKNOWN / incomplete compensation (`OrderFulfillmentWorkflowOrderTest`)
- IT: real services + Temporal for happy path + T-201-1…6 (`*HappyPathIT`, `*CompensationIT`)
- Service-level idempotency / resolve tests in payment/inventory/shipping modules

**Missing for TH-202 (gaps):**

1. No test asserting `ActivityPolicies` numeric values / `doNotRetry` set  
2. No test that a **retryable** activity failure (5xx / simulated) is retried then succeeds  
3. No test that `BUSINESS` fails with **attempt count = 1** (no Temporal retry spin)  
4. No test that **start-to-close / schedule-to-close exhaustion** leads to compensation (and correct outcome)  
5. No test distinguishing Temporal retries vs the **3** resolve attempts  
6. No test for transport error wrapping / connection failure classification  
7. No dedicated lost-response-without-returned-id scenario (protocol F-PAY-AMB edge)  
8. Docs state options but lack rationale vs `T_observe` / `T_run_max`

---

## 7. Proposed implementation plan (minimal)

### 7.1 Code changes (only after Owner plan approval)

**Default (recommended minimal):** **documentation + tests only** — keep current `ActivityPolicies` numbers unless Owner requests retune.

Optional small hardening (**needs Owner OK** if included):

- O1: Explicitly document that current values are **local engineering defaults** for CI/dev, not protocol constants; do not silently rename them to `T_observe`.
- O2: Wrap non-HTTP `RestClient`/`IO` failures as retryable typed `ApplicationFailure` (e.g. `TRANSPORT`) for clearer metrics — behavior change is small but not zero.
- O3: **Do not** implement orderId payment discovery / change timeout→AMBIGUOUS mapping without a new Owner decision (would touch D-201/protocol interpretation).

**Out of scope for TH-202:** F0–F3 harness, experiment runner, protocol edits, console UI, stash apply.

### 7.2 Test cases to add

| ID | Scenario | Expected |
| --- | --- | --- |
| T-202-1 | Unit: `ActivityPolicies` forward/compensation timeouts, max attempts, `doNotRetry` | Exact values as in §1 |
| T-202-2 | Unit: activity fails once with retryable failure then succeeds | Workflow SUCCESS; attempt count ≥ 2 |
| T-202-3 | Unit: activity fails with `BUSINESS` | Single attempt; compensation path as today |
| T-202-4 | Unit: activity exceeds retries / timeout → `ActivityFailure` | Compensation runs; outcome `COMPENSATED_FAILURE` when pendingOutcome unset; IDs reconciled via GET when present |
| T-202-5 | Unit: UNKNOWN path still uses ≤ `resolveAttemptLimit` resolve calls even when activities succeed | Distinct from Temporal retries; AMBIGUOUS still holds |
| T-202-6 | Doc/assert: mutating activities use stable idempotency keys (can be light unit on `HttpOrderFulfillmentActivities` or contract keys) | Keys match `IdempotencyKeys` |

Prefer `TestWorkflowEnvironment` + controllable activity double (extend `RecordingActivities`) over new real-service fault IT, unless a thin IT is needed for one timeout case.

### 7.3 Documentation updates

- Expand `VARIANT_A_HAPPY_PATH.md` §Activity options: rationale table, retry vs resolve-bound distinction, timeout→compensation behavior, explicit **limitations** (lost response without returned IDs; no workflow-level timeout; RestClient timeouts unset).
- Optional short `docs/architecture/VARIANT_A_RETRIES_TIMEOUTS.md` if the happy-path doc grows too large — Owner preference.

### 7.4 Acceptance criteria

1. Retry/timeout policy documented with **exact** values and code locations.  
2. Tests T-202-1…5 green on CI (T-202-6 as practical).  
3. No change to protocol / invariants / oracle files.  
4. No change to D-201 compensation order or UNKNOWN→AMBIGUOUS semantics unless Owner-approved.  
5. TH-201/TH-206 remain Done/Closed; `stash@{0}` untouched.  
6. Handoff lists any deferred gaps (lost-response-without-ID) as limitations, not silent “fixed”.

### 7.5 Estimate

**2 SP** (matches backlog leaf; investigation done → remaining mostly tests + docs).  
If Owner adds O2+O3 behavior work, revise to **3 SP**.

---

## 8. Open questions (Owner)

1. **Keep** current ActivityOptions numbers as engineering defaults, or retune relative to `T_observe`/`T_run_max`?  
2. Include optional **transport-failure wrapping** (O2) in TH-202, or docs/tests only?  
3. Is **lost-response-without-returned-paymentId** in scope for TH-202, a follow-up ticket, or explicitly deferred to TH-400/F3 harness?  
4. Should workflow starters set an explicit **workflow execution timeout** capped by `T_run_max`, or leave unset (harness-only deadline)?  
5. Prefer docs only in `VARIANT_A_HAPPY_PATH.md` vs a dedicated retries/timeouts architecture note?

---

## 9. Stash / protocol lock

- `stash@{0}` preserved.  
- No protocol / invariant / oracle edits in this investigation.  
- D-201 preserved.
