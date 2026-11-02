# HANDOFF_TO_CHATGPT

**Data:** 2026-11-02  
**Od:** Cursor Agent  
**Tryb:** IMPLEMENTATION  
**Task:** TH-402 phase-1 — F0 × Variant A Runner skeleton  
**Authorization:** **D-AUTH approved (phase-1 only)** · D-402 · D-403 · S1

---

## Branch / base

| | |
| --- | --- |
| Branch | `feature/th-402-phase1-runner-skeleton` |
| Base HEAD | `31256cdc288aa071fa9f873a454afc2a1d91c46a` |
| Module | `:experiment:runner` |

## Implemented

- Library + CLI (`RunnerMain`) — no Boot HTTP
- Lifecycle `COMPLETED`/`EXCLUDED`/`ABORTED_EXPERIMENT`
- Serial + `ExperimentEnvLock`
- Fail-closed `JdbcAllowlist` + `StateReset`
- `FileResultStore` JSON+CSV, atomic write, overwrite refuse, persist retry
- `VariantAAdapter` — Temporal start/describe/await/**terminate** (R6)
- `TrialRunner` — R14 quiesce → full `T_observe` → Oracle once; F0 null `faultObservedAt`
- `F0FaultController` no-op
- Unit tests: allowlist, ResultStore, F0, lifecycle (happy / terminate / not-quiesced / wrong DB)

## Tests

```
./gradlew :experiment:runner:test  → BUILD SUCCESSFUL (11 tests)
```

No full-repo CI run claimed in this handoff unless executed separately.

## Acceptance mapping (phase-1)

| # | Criterion | Status |
| --- | --- | --- |
| 1 | F0×A trial path | Implemented (CLI + library; IT against live stack = ops) |
| 2 | Lifecycle statuses persisted | Yes |
| 3 | Serial + env lock | Yes |
| 4 | Allowlist fail-closed + tested | Yes |
| 5 | Deterministic reset + gated | Yes |
| 6 | Variant A Temporal | Yes |
| 7 | Oracle once after observe | Yes (unit); live needs stack |
| 8 | terminate not cancel | Yes |
| 9 | No compensation on terminate | Yes (adapter does not call compensate APIs) |
| 10 | Full 30s observe after quiesce | Yes (injectable clock in tests) |
| 11 | F0 no-op | Yes |
| 12 | JSON+CSV; no re-run on persist fail | Yes |
| 13 | D-R10 skip incomplete / block on quiesce | Block on quiesce yes; matrix skip = CLI single-trial phase-1 |
| 14 | Tests | Unit suite green |
| 15 | Docs | README + coordination + this handoff |

**Phase-1 ≠ full TH-402 Done.** Taiga #37 = **In Progress**, 3 SP, not Done.

## Limitations / deferred

- No live Temporal E2E IT in-module yet (console pattern exists; phase-1 unit coverage)
- F1/F2/F3 / TH-400 not implemented
- Variant C / TH-303 deferred
- Postgres results deferred
- ATOMIC_MOVE may fall back behavior on some FS (standard Java caveat)

## Taiga

#37 → **In Progress**; description notes D-AUTH phase-1; SP=3; full-acceptance deps retained narratively.

## Obsidian

Current Status / Implementation Plan / Decision Log / Weekly log updated for D-AUTH phase-1.

## Next recommended task

Owner review + merge phase-1; then separate auth for **TH-400** (S1 step 2).
