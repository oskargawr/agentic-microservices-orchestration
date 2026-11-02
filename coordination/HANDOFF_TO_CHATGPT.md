# HANDOFF_TO_CHATGPT

**Date:** 2026-11-02  
**From:** Cursor Agent  
**Task:** TH-202 Retries and Timeouts — **investigation complete**; awaiting Owner plan review  
**Repo:** `main` @ `9385fdf`  
**Full write-up:** `coordination/TH-202_INVESTIGATION.md`

---

## 1. Coordination status

| Item | Value |
| --- | --- |
| `NEXT_TASK` | TH-202 **`approved`** (implementation **not** started) |
| TH-201 / TH-206 | **Done / Closed** (preserved) |
| D-201 / protocol / oracle | **unchanged** |
| App code | **no modifications** this phase |
| `stash@{0}` | preserved |

---

## 2. Current implementation findings (Source fact)

`ActivityPolicies`:

| | Forward | Compensation |
| --- | --- | --- |
| Start-to-close | 10s | 10s |
| Schedule-to-close | 60s | 90s |
| Max attempts | 5 | 5 |
| Backoff | 200ms → 2s, coeff 2.0 | same |
| Non-retryable | `BUSINESS`, `COMPENSATION_INCOMPLETE` | same |

- HTTP 4xx → non-retryable `BUSINESS`; 5xx → retryable `HTTP_*` (`HttpOrderFulfillmentActivities.toFailure`).
- UNKNOWN resolve bound = **3** workflow-level attempts (`OrderFulfillmentInput`), **separate** from Temporal retries.
- Mutating calls use stable `IdempotencyKeys`; `resolvePayment` has **no** idempotency header (re-entrant by `paymentId` lock).
- No workflow execution timeout in console/test starters.
- RestClient: no explicit connect/read timeouts configured.

---

## 3. Identified gaps

1. **No tests** for retry/timeout policy behavior (values, retryable vs BUSINESS, timeout→compensation).
2. Docs list options but **lack rationale** vs protocol `T_observe=30s` / `T_run_max=120s`.
3. **Lost response without returned IDs:** if charge/reserve times out after commit, workflow may lack IDs → incomplete compensate-by-id; protocol F-PAY-AMB edge not covered by Variant A workflow tests.
4. Transport errors outside `RestClientResponseException` not explicitly classified (**Assumption:** Temporal retries them).
5. Cancel-signal + detached compensation IT still a known TH-201 follow-up (unchanged; not TH-202 core).

---

## 4. Proposed implementation scope (minimal)

**Default recommendation:** documentation + tests only; **keep** current numeric policies.

| Include | Exclude |
| --- | --- |
| T-202-1…5 (unit) + policy docs expansion | F0–F3 harness / runner (TH-400) |
| Clarify retry vs resolve-bound vs protocol T_* | Protocol / invariant / oracle edits |
| Record lost-response-without-ID as **limitation** | D-201 behavior changes without Owner OK |
| Estimate **2 SP** | Unrelated console / stash |

Optional (needs Owner OK): wrap transport failures as typed retryable failures; set workflow timeout vs `T_run_max`; retune ActivityOptions numbers.

---

## 5. Test plan (proposed)

| ID | Intent |
| --- | --- |
| T-202-1 | Assert exact `ActivityPolicies` values / `doNotRetry` |
| T-202-2 | Retryable failure then success → SUCCESS, attempts ≥ 2 |
| T-202-3 | `BUSINESS` → single attempt + existing compensation semantics |
| T-202-4 | Retry/timeout exhaustion → compensation; default `COMPENSATED_FAILURE` when no pending AMBIGUOUS |
| T-202-5 | UNKNOWN path resolve call count ≤ bound; distinct from Temporal retries |
| T-202-6 | Idempotency key stability for mutating activities (light) |

---

## 6. Acceptance criteria (proposed)

1. Exact retry/timeout config documented with code locations.  
2. T-202-1…5 green in CI.  
3. No protocol/oracle/D-201 semantic drift unless Owner-approved.  
4. Deferred gaps listed explicitly (lost-response-without-ID).  
5. TH-201/206 stay Closed; stash untouched.

---

## 7. Open questions for Owner

1. Keep current ActivityOptions numbers or retune vs `T_observe`/`T_run_max`?  
2. Docs/tests only, or also transport-failure wrapping?  
3. Lost-response-without-returned-ID: TH-202, later ticket, or TH-400?  
4. Set workflow execution timeout capped by `T_run_max`, or harness-only?  
5. Docs in `VARIANT_A_HAPPY_PATH.md` only vs dedicated retries note?

**Stop condition:** wait for Owner review of this plan before any application code changes.
