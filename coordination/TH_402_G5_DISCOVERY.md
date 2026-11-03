# TH-402 G5 — Live Temporal runner pre-pilot (discovery / proposal)

| Field | Value |
| --- | --- |
| **Status** | **proposal** — implementation **NOT** authorized |
| **Date** | 2026-11-03 |
| **Mode** | RESEARCH / discovery only |
| **Related** | OWN-P4 · D-AUTH-1.1 · D-402 · D-403 · D-408 · Taiga #37 |
| **Does not include** | S4-D · F1–F3 live fault campaigns · Variant C · EXP-* confirmatory runs |

## 1. Purpose

OWN-P4 defines **G5** as a **pre-pilot gate** (not a merge gate): before any actual experiment pilot, demonstrate that the **experiment runner** can drive a **real** Temporal cluster through:

1. Workflow **start**
2. **Natural completion**
3. **Termination** (R6 / `T_run_max` path)
4. **Oracle evaluation** (DB-primary IndependentOracle once per trial)

This closes the gap between mocked/`TestWorkflowEnvironment` runner tests and a trustworthy lab pilot.

## 2. Relationship to prior decisions

| Decision | Relationship to G5 |
| --- | --- |
| **OWN-P4** | Defines G5 as pre-pilot (approved clarification) |
| **D-AUTH-1.1** | Authorized phase-1.1 remediation + merge-gate G1–G4, G6–G8; **explicitly excluded G5** |
| **D-AUTH (phase-1)** | Skeleton only; G5 never in scope |
| **D-408 / S4** | Matrix driver on `main`; does **not** satisfy G5 or S4-D |

**Authorization required:** a **new** Owner D-AUTH (e.g. D-AUTH-G5) before any G5 implementation or live execution. OWN-P4 alone is not an implementation grant.

## 3. Separation from TH-400 S4-D / CI Temporal

| Artifact | What it proves | Not G5 |
| --- | --- | --- |
| `ConsoleRealTemporalE2EIT` (CI #65) | Console BFF + worker happy-path on Compose Temporal (TH-206) | No runner lifecycle, no TrialRunner, no terminate/Oracle harness contract |
| CompIT `TestWorkflowEnvironment` | Deterministic saga / retry policy | In-process Temporal test server |
| **S4-D** (deferred) | Live **fault-profile** F1–F3 matrix cells | Broader than G5; still unauthorized |
| **G5 (proposed)** | Runner × live Temporal: start / complete / terminate / Oracle | F0-focused pre-pilot; not full F1–F3 campaign |

Recommended order: **G5 before S4-D** (and before inventing EXP-* fault campaigns).

## 4. Required infrastructure / environment

| Component | Requirement |
| --- | --- |
| Temporal | Reachable cluster (Compose `localhost:7233` or lab); namespace `default` (or documented) |
| Variant A worker | Running against same task queue as runner adapter |
| Business services | Inventory / Payment / Shipping + allowlisted Postgres |
| Runner | `RunnerMain` / `TrialRunner` with JDBC allowlist + env lock |
| Results root | Writable path for durable trial JSON |
| Fault profile | **F0** sufficient for G5 (no injection URLs required) |
| Timing | Protocol-v0 constants **unchanged** (`T_observe`, `T_run_max`, quiesce) |

## 5. Proposed test cases (acceptance sketch)

| ID | Case | Expected evidence |
| --- | --- | --- |
| G5-1 | **Start** | Durable trial shows workflowId/runId; Temporal execution visible |
| G5-2 | **Natural completion** | Workflow reaches terminal COMPLETED without harness terminate; Oracle runs once; harness `COMPLETED` only after durable JSON (OWN-P1) |
| G5-3 | **Termination** | Force `T_run_max` / non-terminal path → Temporal **terminate** (not cancel); terminatedBy recorded; further isolation OK |
| G5-4 | **Oracle** | IndependentOracle classification present on completed trial; DB-primary; no classifier change |

Optional (still G5, not S4-D): single F0 repetition under matrix driver with `plannedCells=1`.

**Out of scope for G5:** F1-P / F2 / F3 live injection campaigns, multi-profile matrix pilot archive, confirmatory EXP-*.

## 6. Required evidence / artifacts

- Git SHA of runner + worker  
- Environment description (Compose vs lab)  
- Trial JSON(s) under `experiment/results/…`  
- Optional: Temporal UI / `describe` screenshots or CLI dumps  
- Short run note in `EXPERIMENT_LOG.md` **only if** Owner treats G5 as an indexed lab exercise (may use a non-EXP lab tag — Owner decides; do not invent EXP-* confirmatory labels)  
- Explicit statement: evidence ≠ S4-D ≠ thesis pilot campaign

## 7. Authorization & PM boundaries

| Action | Allowed now? |
| --- | --- |
| This discovery doc | Yes |
| Implement G5 tests / scripts | **No** until D-AUTH |
| Execute live G5 | **No** until D-AUTH |
| Mark Taiga #37 Done after G5 | **No** (full TH-402 Done still needs broader acceptance) |
| Start S4-D | **No** |
| Change Oracle / protocol | **No** |

## 8. Open questions for Owner

1. Authorize G5 next (D-AUTH-G5), or prioritize something else (methods writing, Variant C discovery, S4-D)?  
2. Is Compose Temporal sufficient for G5, or is a dedicated lab cluster required?  
3. Should a successful G5 leave an indexed row in `EXPERIMENT_LOG` (lab) before any EXP-* pilot?  
4. After G5, is S4-D the next TH-400 slice, or is methodology/writing higher priority?

## 9. Recommendation

Propose **TH-402 G5** as the next engineering research milestone under a **new Owner authorization**, keeping **S4-D** explicitly separate and deferred.
