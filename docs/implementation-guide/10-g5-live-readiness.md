# 10 — G5 live readiness

**SoT requirements:** `coordination/TH_402_G5_DISCOVERY.md`  
**Auth:** D-AUTH-G5 (implement) · D-409 (merged; live still unauthorized)  
**Code location:** `experiment/runner/src/test/java/.../g5/` (+ some helpers used by IT)

## Status snapshot

| Aspect | Status |
| --- | --- |
| Implementation on `main` | **merged** (PR #16 → `801e343`) |
| Post-merge CI | **#71 SUCCESS** (Owner-reported) on merge commit |
| Live G5-2 / G5-3 execution | **not** authorized / **not** live-tested |
| TH-402 / Taiga #37 Done | **must remain not Done** |
| EXP-* | **none** |

## G5 acceptance matrix

| Requirement | Scenario | Preconditions | Procedure | Expected result | Evidence | Failure behavior |
| --- | --- | --- | --- | --- | --- | --- |
| Real start + ids | G5-1 | Opt-in ENV + Temporal | Start via adapter/runner | Non-null workflowId/runId | IT asserts / TrialRecord ids | Fail test; no fake ids |
| Natural completion | G5-2 | Worker pollers present; scenario=G5-2 | TrialRunner F0 await natural stop | `terminatedBy=WORKFLOW_STOP`; Oracle; COMPLETED record | TrialRecord JSON | No R6 terminate counted as success path |
| Timeout terminate | G5-3 | **No** pollers; scenario=G5-3 | Preflight → start → 120s → terminate → quiesce → 30s observe → Oracle → history | Temporal TERMINATED; acceptance VALID iff gates pass | TrialRecord + `g5-3-acceptance.json` | Fail-closed preflight; EXCLUDED ≠ VALID |
| Oracle independence / once | G5-4 | TrialRunner path | Single `oracle.evaluate` | Exactly one evaluation | `G5OracleCallCounter` / unit test | Double-call fails test |

## Why COMPLETED ≠ G5-3 VALID

`TrialRunner` may persist `harnessStatus=COMPLETED` after R6 terminate + observe + Oracle. G5-3 **VALID** additionally requires:

- Queue exclusivity preflight (DescribeTaskQueue; API errors ⇒ not exclusive)  
- Full history pagination (`nextPageToken` drained)  
- History event constraints (e.g. terminate after scheduled/started workflow task evidence per gate)  
- Writer writes acceptance file **only** for VALID  

## Opt-in flags (do not set in this documentation work)

- `THESIS_RUN_G5=true`  
- Scenario selector (G5-2 / G5-3 independently)  
- Dedicated ENV markers as required by `G5OptIn` / ENV lock  

## Key classes

| Class | Role |
| --- | --- |
| `G5OptIn` | Flag parsing; default disabled |
| `G5LabSupport` | Shared lab wiring |
| `G5IsolationPreflight` | No-poller exclusivity |
| `DescribeTaskQueueG5QueuePollerProbe` | Real DescribeTaskQueue client probe |
| `EnvFlagG5QueuePollerProbe` | Env-based probe (tests / alternate) |
| `G5HistoryFetcher` | Paginated history |
| `G5HistoryGate` | Acceptance predicate on history |
| `G5AcceptanceEvaluator` / `G5AcceptanceWriter` / `G5AcceptanceRecord` | VALID/INVALID + JSON |
| `RunnerLiveTemporalG5IT` | Opt-in live scenarios |
| `CountingVariantAdapter` | Terminate call counting (≠ cancel) |

## OWN-P1 / OWN-P4

- **OWN-P1:** Protocol/owner constraint referenced by G5 discovery (do not weaken in lab code).  
- **OWN-P4:** G5 as **pre-pilot gate**, not a merge gate — merge of lab code ≠ experimental success.
