# 12 — Glossary

Format: **Term** — full name · plain meaning · why it exists · where · kind (requirement / phase / scenario / test / governance / code).

## Experimental profiles

| Term | Full / plain | Why | Where | Kind |
| --- | --- | --- | --- | --- |
| **F0** | Fault profile zero — no injection | Baseline cell | `F0FaultController` | runtime profile |
| **F1-P** | Fault 1 Payment — `CHARGE_FAIL` after HELD | Deterministic payment business fail | `F1PFaultController` | runtime profile |
| **F2** | Fault 2 — `ARRANGE_REJECTED` after CAPTURED | Shipping fail after money captured | `F2FaultController` | runtime profile |
| **F3-Recovered** | Lost charge response, resolve visible | Ambiguity that **recovers** | `F3RecoveredFaultController` | runtime profile |
| **F3-Unresolved** | Lost charge + opaque resolve | Ambiguity that **stays** unresolved | `F3UnresolvedFaultController` | runtime profile |
| **F1 / F3 (bare)** | Invalid shorthand | Ambiguous; rejected by harness | `CanonicalFaultProfiles.requireCanonical` | anti-pattern |
| **OraclePattern F0/F1/F2** | Classifier explanatory patterns | TH-011 patterns ≠ profile IDs | `OraclePattern` enum | classifier metadata |

## Development phases

| Term | Plain | Why | Where | Kind |
| --- | --- | --- | --- | --- |
| **TH-400** | Runner / fault-matrix epic | Engineering delivery of harness | coordination, Taiga | epic / phase |
| **S0…S4** | Implementation slices | Incremental harness delivery | DECISION_LOG, PROJECT_STATE | **implementation phases** (not faults) |
| **S4-A/B/C** | Matrix, expected cells, gates | Complete matrix readiness without S4-D | D-408 | phases (merged) |
| **S4-D** | Deferred slice | Out of auth | DECISION_LOG | deferred |
| **TH-402** | G5 Temporal readiness ticket/epic | Pre-pilot live gate work | D-AUTH-G5, D-409 | epic |
| **G5** | Live Temporal evidence gate | Prove runner against real Temporal | `g5/*`, discovery doc | pre-pilot gate |
| **G5-1** | Start + identify workflow | Prove real start | `RunnerLiveTemporalG5IT` | criterion/scenario |
| **G5-2** | Natural completion with worker | Prove WORKFLOW_STOP path | same | **live scenario** |
| **G5-3** | No poller + R6 terminate + history | Prove terminate evidence path | same | **live scenario** |
| **G5-4** | Oracle once / independence evidence | Evidence integrity | Oracle counter tests | criterion |

## Protocol and acceptance

| Term | Plain | Why | Where | Kind |
| --- | --- | --- | --- | --- |
| **R6** | Terminate at `T_run_max` if still running | Bound trial wall time | `TrialRunner` terminate | protocol rule |
| **R14** | Observe window after workflow stop | Stabilize before Oracle | `tObserve` sleep | protocol rule |
| **T_run_max** | 120s max run | Bound wait | `RunnerTiming.PROTOCOL_T_RUN_MAX` | protocol constant |
| **T_observe** | 30s observe | Post-stop wait | `PROTOCOL_T_OBSERVE` | protocol constant |
| **quiescence** | Temporal execution terminal after wait | Distinguish stop vs still running | `awaitStop` quiesce; default wait **5s** (`DEFAULT_QUIESCE_WAIT`) | engineering wait (with R6/R14 protocol timings) |
| **WorkflowTaskScheduled / Started** | Temporal history event types | G5-3 evidence of task lifecycle | history gate | Temporal events |
| **WorkflowExecutionTerminated** | History event for terminate | Prove R6 path | history gate | Temporal event |
| **OWN-P1** | Owner protocol constraint (discovery) | Must not weaken semantics | G5 discovery / decisions | requirement |
| **OWN-P4** | G5 is pre-pilot not merge gate | Merge ≠ experiment success | D-409, discovery | requirement |
| **TrialRecord** | Durable trial artifact | Evidence package | `TrialRecord.java` | code artifact |
| **HarnessStatus** | COMPLETED / EXCLUDED / ABORTED_EXPERIMENT | Harness-level outcome | `HarnessStatus` | code enum |
| **temporalStatus** | Temporal describe status string | Separate from harness | TrialRecord field | runtime signal |
| **terminatedBy** | WORKFLOW_STOP vs T_RUN_MAX | Why execution ended from harness view | `TerminatedBy` | code enum |
| **OracleSummary** | Persisted classification summary | Embed Oracle result in TrialRecord | model package | code |
| **g5-3-acceptance.json** | G5-3 VALID acceptance file | Explicit lab acceptance artifact | `G5AcceptanceWriter` | evidence artifact |
| **ENV lock** | Dedicated environment gate | Prevent destructive ops elsewhere | ExperimentEnvLock | safety |
| **JDBC allowlist** | Allowed DB targets | Fail-closed DB safety | JdbcAllowlist | safety |
| **fail-closed** | On uncertainty, treat as failure/unsafe | Prefer false negative over false VALID | probes, verify, gates | design rule |

## Governance

| Term | Plain | Why | Where | Kind |
| --- | --- | --- | --- | --- |
| **D-AUTH-G5** | Decision authorizing G5 **implementation** | Scoped lab build | DECISION_LOG | governance |
| **D-409** | Post-merge G5 reconciliation | Record merge ≠ live | DECISION_LOG | governance |
| **D-404** | Prior related decision (see log) | Historical SoT — do not rewrite | DECISION_LOG | governance |
| **D-408** | S4-A/B/C merge record | Matrix on main | DECISION_LOG | governance |
| **D-011** | Oracle Option C | Independence + labels | DECISION_LOG + classifier | governance |
| **PR** | Pull request | Code review merge unit | GitHub | process |
| **CI** | Continuous integration | Automated verification | GitHub Actions | process |
| **CompIT** | Component IT with TestWorkflowEnvironment | Saga tests without full lab | variant-a tests | test level |
| **E2E** | End-to-end against real Temporal (gated) | Console/G5 live | Console/G5 IT | test level |
| **EXP-*** | Experiment run identifiers | Measured campaigns | EXPERIMENT_LOG | experimental record |
| **NEXT_TASK** | Current authorized task file | Execute only if `approved` | `coordination/NEXT_TASK.md` | governance |
| **PROJECT_STATE** | Living project snapshot | Session start SoT | `coordination/PROJECT_STATE.md` | governance |
| **EXPERIMENT_LOG** | Index of EXP runs | Never invent results | `coordination/EXPERIMENT_LOG.md` | governance |

## Unresolved

| Term | Note |
| --- | --- |
| Exact verbatim text of **OWN-P1** in Owner docs | Referenced by discovery/decisions; quote Owner vault if needed before oral exam |
| Full scope text of **S4-D** deliverables | Explicitly unauthorized — do not invent |
