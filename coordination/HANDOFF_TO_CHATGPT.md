# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION  
**Task:** TH-402 phase-1.1 remediation (D-AUTH-1.1)  
**Authorization:** D-AUTH-1.1 **APPROVED** · OWN-P1–P4 · D-402 · D-403

---

## Branch

| | |
| --- | --- |
| Branch | `feature/th-402-phase1-1-remediation` |
| Base | `740f831d08a1d792fd76e14d053ec25b47e38a18` |
| Phase-1 commits preserved | `c862331` · `f2b0744` · `740f831` |

## Finding acceptance

| ID | Status |
| --- | --- |
| **A** JDBC connection-metadata gate | Done — `JdbcConnectionGate` before destructive SQL; G1/G2 IT green |
| **B** OWN-P1 persistence | Done — durable-only COMPLETED; `.partial` + EXCLUDED/PERSISTENCE_FAILURE; G8 |
| **C** Terminate race | Done — re-describe; natural terminal ≠ crash; never cancel; G4 |
| **E** OWN-P3 abort | Done — `experiment-status.json` / `ExperimentAbort`; no trial rewrite |
| **F** CSV idempotent | Done — rebuild from JSON SoT |
| **D** T_run_max | No change (OWN-P2) |

## Merge-gate tests (executed)

```
./gradlew :experiment:runner:test
→ 24 tests, 0 failures, 0 errors, 0 skipped
  (includes StateResetJdbcIT G1/G2 against live Postgres :5433)

./gradlew test
→ BUILD SUCCESSFUL
```

| Gate | Evidence |
| --- | --- |
| G1 | `StateResetJdbcIT.g1_connectionMetadataMismatch…` |
| G2 | `StateResetJdbcIT.g2_stateResetOnAllowlistedDbIsIdempotent` |
| G3 | `ExperimentEnvLockIT` (same-JVM + cross-process + threads) |
| G4 | `TrialRunnerLifecycleTest` race tests |
| G6 | happy-path full 30s observe |
| G7 | `verify(oracle, times(1))` |
| G8 | persist failure preserves oracle + `.partial` |

## Deferred

- **G5** live Temporal — **pre-pilot gate only** (OWN-P4); not merge gate  
- TH-400 / phase-2 / Variant C  
- No push / PR / merge / Taiga #37 Done  

## Next Owner action

Review phase-1.1 branch; separately authorize push/PR if desired. Before pilot: authorize/require G5. Then S1 → TH-400 auth.
