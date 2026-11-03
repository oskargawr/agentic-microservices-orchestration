# 06 — Experimental / implementation phases

**Critical distinction:** **S0–S4** are **project implementation slices** (engineering milestones). They are **not** fault profiles. Fault profiles are **F0…F3-***.

## Project phase matrix

| Phase | Objective | Main implementation | Evidence | Status | Remaining |
| --- | --- | --- | --- | --- | --- |
| **S0** | Catalogue + contracts for runner faults | `CanonicalFaultProfiles`, docs | code + decisions | **merged** | — |
| **S1** | F0 harness lifecycle | `TrialRunner`, `F0FaultController`, ResultStore | CompIT/unit + CI | **merged** | — |
| **S2** | F1-P + F2 controllers + lifecycle | F1P/F2 controllers, verifiers | tests + CI | **merged** | — |
| **S3** | F3-Recovered / F3-Unresolved | F3 controllers + payment opacity | tests + CI | **merged** | — |
| **S4-A** | Matrix driver wiring | `MatrixExperimentDriver` | tests + CI | **merged** (PR #15 / D-408) | — |
| **S4-B** | Expected cell outcomes | `ExpectedCellOutcome`, `MatrixSummary` | tests + CI | **merged** | — |
| **S4-C** | F3-Unresolved gate + ENV hardening | acceptance gate, allowlist | tests + CI | **merged** | — |
| **S4-D** | (deferred slice) | — | — | **deferred / unauthorized** | Owner D-AUTH required |
| **TH-402 G5** | Live Temporal pre-pilot gate lab | `experiment/runner/.../g5/*`, `RunnerLiveTemporalG5IT` | PR #16 merge + CI #71 | **implemented / merged / CI-verified**; **not live-tested** | Owner live auth |
| **EXP-*** | Measured experimental campaigns | — | `EXPERIMENT_LOG` | **none** | after live readiness |

## G5 vs S4-D

| | G5 | S4-D |
| --- | --- | --- |
| Nature | Pre-pilot **live Temporal** evidence gate (OWN-P4) | Deferred **implementation** slice (unauthorized) |
| On `main`? | Lab **code** yes (PR #16) | No |
| Live runs? | Not authorized | N/A |
| Fault matrix? | Uses F0 (and harness) for Temporal evidence — **not** four fault profiles named G5-1…4 | Separate (undefined here) |

## G5-1…G5-4 are scenarios / criteria — not fault profiles

| Id | Kind | What it checks |
| --- | --- | --- |
| **G5-1** | Criterion / scenario | Real Temporal **start** + workflow identification |
| **G5-2** | **Independent live scenario** | Worker **UP** → natural completion → Oracle → durable COMPLETED `TrialRecord` |
| **G5-3** | **Independent live scenario** | **No pollers** → 120s → R6 terminate → quiesce → observe → Oracle → history gate → `g5-3-acceptance.json` |
| **G5-4** | Cross-cutting criterion | Independent Oracle exactly-once / evidence integrity on TrialRunner path |

G5-2 and G5-3 must not be conflated (D-AUTH-G5 / discovery). Cross-cutting items (opt-in flags, ENV, DescribeTaskQueue fail-closed) apply across scenarios.

## Governance phases / tickets

| Id | Role |
| --- | --- |
| **TH-400** | Runner / fault-matrix epic (S0–S4) |
| **TH-402** | G5 live Temporal readiness (implementation ≠ ticket Done) |
| **D-AUTH-G5** | Authorized **implement-only** G5 lab |
| **D-409** | Post-merge coordination: G5 on main, live still unauthorized |
| **D-408** | S4-A/B/C merge coordination |
| **D-404** | (see DECISION_LOG — prior G5/discovery related; do not rewrite) |
| **D-011** | Oracle Option C / independence |
