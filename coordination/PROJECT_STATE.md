# PROJECT_STATE

**Last updated:** 2026-11-03 (D-AUTH-G5 implementation delivery)  
**Updated by:** Cursor Agent  
**MODE this session:** IMPLEMENTATION (G5 test/lab only; no live G5; no production lifecycle edits)

## Current thesis scope (MVP)

| | |
| --- | --- |
| **Runner** | TH-402 phase-1 + 1.1 + F0–F3 **matrix driver** on `main` |
| **TH-400 S0–S4-A/B/C** | On `main` (S4 merge `5982c1a` / PR #15) |
| **TH-400 S4-D** | **Deferred / unauthorized** (live Temporal fault-profile lab) |
| **TH-400 overall** | **In Progress** (≠ Done) — harness ready; **no** EXP-* campaign |
| **TH-402 G5** | **Implementation available for review** (D-AUTH-G5); **live execution pending**; ticket **NOT Done** |
| **Not authorized now** | Live G5 · G5 PR merge · S4-D · Variant C · Oracle changes · #35/#37 Done · #68 |

## Engineering

| | |
| --- | --- |
| **Base `main` HEAD (branch point)** | `563c8a0db52d9c55b7fddb96073cf6c861ccafa0` |
| **Feature branch** | `feature/th-402-g5-live-temporal-runner` |
| **D-AUTH-G5** | Approved for G5-1…G5-4 **test/lab** impl; live + merge **not** authorized |
| **Oracle / protocol / R6/R14 / OWN-P1** | Unchanged (production) |
| **EXPERIMENT_LOG** | Unchanged |
| **EXP-*** | **None** |

## G5 deliverables (test/lab)

- Opt-in `RunnerLiveTemporalG5IT` (`THESIS_RUN_G5`) — **not executed** in this delivery  
- G5-3 history gate (`EventType.EVENT_TYPE_WORKFLOW_TASK_STARTED` / `EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED`, `eventId` order)  
- Separate durable `g5-3-acceptance.json` (TrialRecord immutability)  
- Isolation preflight + README lab section  

## Next three actions

1. Owner: review G5 implementation PR  
2. Separate auth for merge (if accepted)  
3. Separate auth for live `THESIS_RUN_G5` execution — do **not** mark Taiga #37 Done  
