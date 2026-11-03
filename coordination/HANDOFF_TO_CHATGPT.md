# HANDOFF_TO_CHATGPT

**Data:** 2026-11-03  
**Od:** Cursor Agent  
**Tryb:** REVIEW REMEDIATION — TH-400 S4 / PR #15; merge **NOT** authorized  
**Branch:** `feature/th-400-s4-matrix-readiness`

---

## Authorization

| | |
| --- | --- |
| Decision | **D-AUTH-S4** (immutable) — A/B/C only |
| This turn | Owner review remediation on PR #15 |
| Deferred / excluded | **S4-D** · Variant C · Oracle · protocol · #68 · #35/#37 Done · **merge** |

## Remediation summary (PR #15)

### P1 — Matrix planned-cell accounting
- `MatrixSummary.plannedCells` = `faultProfileIds().size() * n` (not `trials.size()`)
- Separate counters: `matched` / `mismatched` / `excluded` / `notExecuted` / `executedCells`
- `allMatched` false when any planned cell missing, excluded, or mismatched
- No synthetic successful `TrialRecord`s for never-executed cells

### P1 — Quantitative retry evidence
- `ActivityPolicies.lostIdRecoveryOptions()` public (moved from workflow impl)
- `RecordingActivities`: `forwardChargeInvocations` vs `recoveryChargeInvocations`
- S4-C CompIT asserts forward **5**, recovery **1**, `maximumAttempts=1` on recovery, no SUCCESS/consume
- Documented: Temporal `maximumAttempts=5` = five **total** executions; evidence under `TestWorkflowEnvironment` only

### P1 — Lifecycle ordering
- Mocked matrix test records reset/arm/verify/disarm/start/awaitStop/oracle
- Asserts cell1 reset→arm; disarm:end before cell2 reset; no overlapping workflows; abort still blocks

### P2 — Schedule-aware CLI
- Multi-profile → full concurrent registration (both URLs)
- Single F2 → shipping only; single F1-P/F3 → payment only; F0 → none
- Fail-closed verify for selected profiles unchanged

### Disarm regression
- Composite disarm clears relevant armed map; double-disarm safe; inactive map unaffected
- Existing matrix abort-on-disarm-failure retained (+ planned/notExecuted asserts)

## Tests (rerun)

```text
java -jar gradle/wrapper/gradle-wrapper.jar \
  :orchestration:variant-a:test :experiment:runner:test :services:payment:test --rerun-tasks
→ BUILD SUCCESSFUL
variant-a 39 / 0 / 0
runner 98 / 0 / 0
payment 14 / 0 / 0
```

### New / renamed tests (selected)
- `MatrixSummaryTest.*` (complete / early abort / exclusion / mismatch / empty)
- `MatrixExperimentDriverTest.lifecycleOrdering_betweenTwoCells_isSerial_resetArmDisarm`
- `RunnerMainFaultControllerTest.*`
- `CompositeFaultControllerTest.disarm_removesArmedState_idempotent_andInactiveControllersUnaffected`
- `OrderFulfillmentRetryTimeoutTest.s4c_defaultOptions_exhaustedRetryableCharge_forwardFive_recoveryOne`

**Layers:** unit · CompIT (`TestWorkflowEnvironment`) · mocked lifecycle — **no** live Temporal cluster.

## Scope confirmation

Unchanged: Oracle semantics · protocol constants · S4-D · Variant C · #68 · Taiga #35/#37 Done status · D-AUTH-S4 text.  
**No merge / auto-merge performed.**

## Ask

Owner: re-review PR #15 → separate **merge** auth if accepted.
