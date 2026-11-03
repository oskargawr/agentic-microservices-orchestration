# TH-402 G5 — Live Temporal runner pre-pilot (discovery / acceptance specification)

| Field | Value |
| --- | --- |
| **Status** | **accepted contract** — implementation authorized by **D-AUTH-G5** (2026-11-03); **live G5 execution** and **merge** still require separate Owner auth |
| **Date** | 2026-11-03 (discovery closure: history eventId ordering + `g5-3-acceptance.json`) |
| **Mode** | ANALYSIS / DOCUMENTATION only |
| **Related** | OWN-P4 · D-AUTH-1.1 · D-402 / R6 / R14 · D-012 · OWN-P1 · D-408 · Taiga #37 |
| **Does not include** | S4-D · F1–F3 live fault campaigns · Variant C · EXP-* confirmatory · protocol/Oracle edits |

**Authorization required:** new Owner **D-AUTH-G5**. OWN-P4 defines the gate; it is **not** an implementation grant (D-AUTH-1.1 explicitly excluded G5).

---

## 1. Purpose

Demonstrate that the **experiment runner** (not the console BFF) drives a **real** Temporal server through the approved harness contract:

1. **G5-1** — real workflow start + id capture  
2. **G5-2** — natural completion + full R14 observe + Oracle once + OWN-P1  
3. **G5-3** — real `T_run_max` → Temporal **terminate** (R6) → quiesce → full `T_observe` → Oracle  
4. **G5-4** — DB-primary Independent Oracle exactly once after the required sequence  

This closes the gap between mocked/`TestWorkflowEnvironment` runner tests and a trustworthy lab pilot. It does **not** complete TH-402, does **not** mark #37 Done, and does **not** substitute for S4-D.

---

## 2. Binding decisions (do not reopen)

| Decision | Binding content for G5 |
| --- | --- |
| D-012 / `RunnerTiming` | `T_observe=30s`, `T_run_max=120s` from trial start (OWN-P2 = D0) |
| D-402 R6 | On deadline while RUNNING: Temporal **terminate**, never cancel, never harness compensation |
| D-402 R14 | After terminate: establish Temporal quiescence → if OK, **full** `T_observe` → Oracle once; if not → `EXCLUDED`/`WORKFLOW_NOT_QUIESCED` + block next trial |
| OWN-P1 | `COMPLETED` only after durable JSON; on persist fail keep Oracle in `.partial` + durable `EXCLUDED`/`PERSISTENCE_FAILURE`; no re-run |
| OWN-P4 | G5 = **pre-pilot** gate, **not** merge gate |
| D-AUTH-1.1 | G5 excluded from prior remediation auth |
| D-011 | Terminate / timeout alone never auto-assigns `CONFIRMED_FAILURE` |

If a proposed G5 technique would require changing any row above → **stop**; escalate to Owner (do not “fix around” the contract).

---

## 3. Implementation traceability matrix

Call sequence for one trial (inspected on `main`):

```text
RunnerMain.main
  → ExperimentEnvLock.acquire
  → StateReset (via TrialRunner) / IndependentOracle / VariantAAdapter
  → MatrixExperimentDriver.runAll → TrialRunner.run(TrialSpec)
       assertSafe → resetToProtocolBaseline
       → FaultController.arm/verify (F0: NONE)
       → VariantAAdapter.start
       → awaitStop until t0+T_run_max
       → [if still RUNNING] describe race → terminate(R6) → awaitStop(quiesceWait)
       → clock.sleep(T_observe)  // R14
       → IndependentOracle.evaluate  // exactly one call site
       → FileResultStore.saveTrial (OWN-P1)
       → finally FaultController.disarm
```

| # | Concern | Class / method | Current behavior | Existing coverage | Live Temporal gap | Prod code change for G5? |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Entry / experiment lifecycle | `RunnerMain.main`; `MatrixExperimentDriver.runAll`; `TrialRunner.run` | CLI builds env, acquires lock, runs matrix (n×profiles) via `TrialRunner` | Mocked matrix + lifecycle unit tests | No runner IT against real Temporal | **No** |
| 2 | Workflow start + ids | `VariantAAdapter.start` | `WorkflowClient.start(stub::fulfill, input)` on `OrderFulfillmentWorkflow.TASK_QUEUE` (`variant-a-order-fulfillment`); returns `ExecutionHandle(workflowId, runId)` from Temporal | Mocked start returns; adapter unit tests for fault hints | No assert that real Temporal stores the execution | **No** |
| 3 | Natural completion detect | `VariantAAdapter.awaitStop` / `describe`; `TrialRunner` when `stop.terminal()` | Polls `DescribeWorkflowExecution` until terminal or deadline; terminal → `terminatedBy=WORKFLOW_STOP` | `TrialRunnerLifecycleTest.g6_g7_*` (mocked) | No live natural COMPLETED path through runner | **No** |
| 4 | `T_run_max` deadline | `TrialRunner.run`: `deadline = t0.plus(environment.timing().tRunMax())` | Wall clock from trial start (OWN-P2); defaults `RunnerTiming.PROTOCOL_T_RUN_MAX=120s`; CLI can override `--t-run-max-ms` (**G5 must not shorten**) | Mocked non-terminal await | Live path never hit with F0 (workflow finishes ≪120s) | **No** (technique below) |
| 5 | Terminate vs cancel (R6) | `VariantAAdapter.terminate` → `WorkflowStub.terminate(reason)`; `TrialRunner` reason `"T_run_max"` | **Terminate only**; no `cancel` in adapter/workflow impl | `tRunMaxUsesTerminateNotCancel*` + G4 race tests (mocked) | No live terminate via runner | **No** |
| 6 | Quiescence (R14) | Second `awaitStop(now+quiesceWait)` (default 5s) | Non-terminal → `EXCLUDED`/`WORKFLOW_NOT_QUIESCED` + `blockFurtherTrials=true`; **no Oracle** | `notQuiescedExcludesAndBlocksNext` (mocked) | No live quiesce-after-terminate | **No** |
| 7 | Full `T_observe` | `clock.sleep(environment.timing().tObserve())` **after** quiesced terminal | Always full duration when quiesced (normal **and** forced); default 30s | G6 unit asserts `clock.slept` contains 30s | Live wall-clock 30s not measured through runner | **No** |
| 8 | Oracle exactly-once | Single `oracle.evaluate(...)` call site in `TrialRunner.run` after observe | DB-primary `IndependentOracle` → `OracleSnapshotRepository.load` → `OutcomeClassifier` (no Temporal/HTTP labels as inputs) | G7 unit `verify(oracle, times(1))`; never() when not quiesced | No live runner→Oracle JDBC path | **No** |
| 9 | Durable persist (OWN-P1) | `persistCompletedOwnP1` → `FileResultStore.saveTrial`; fail → `.partial` + `EXCLUDED`/`PERSISTENCE_FAILURE` | COMPLETED only after JSON commit; no workflow/Oracle re-run | G8 persist-failure tests (mocked store) | Live FS + real trial JSON | **No** |
| 10 | Allowlist + StateReset | `RunnerEnvironment.assertSafeForDestructiveOps`; `StateReset.resetToProtocolBaseline`; `JdbcConnectionGate` | Fail-closed before destructive SQL | G1/G2 JDBC ITs | Same stack as pilot | **No** |
| 11 | Env lock | `ExperimentEnvLock.acquire` / `close` (try-with-resources in `RunnerMain`) | Cross-process file lock + in-process mutex; busy → `ENV_BUSY` | `ExperimentEnvLockIT` | IT must acquire/release cleanly | **No** |
| 12 | Worker / queue / services | `VariantAWorkers.startWorkerFactory`; `WorkerRuntime`; task queue `variant-a-order-fulfillment` | **Runner does not start a worker**; separate `:orchestration:worker` or console-started factory | `ConsoleRealTemporalE2EIT` (console, **≠ G5**) | G5 must provision worker+HTTP services explicitly | **No** prod change; **yes** test/lab wiring |

**Contract conflict check:** No approved constant or R6/R14 semantic must change for G5. The only hard problem is **how to keep the workflow RUNNING until real `T_run_max`** without shortening timings — see §5 G5-3.

---

## 4. Layer distinction (do not conflate)

| Layer | Example | Satisfies G5? |
| --- | --- | --- |
| `TestWorkflowEnvironment` | Variant A CompIT / retry tests | **No** |
| Mocked `TrialRunner` | `TrialRunnerLifecycleTest` G4/G6/G7/G8 | **No** (merge gates only) |
| Real Temporal + **console** | `ConsoleRealTemporalE2EIT` (CI #65) | **No** — no `TrialRunner`, no R6/R14 harness, no OWN-P1 trial JSON |
| Real Temporal + **runner** (this proposal) | G5 IT / controlled lab | **Yes** (target) |
| Live F1–F3 fault matrix | S4-D | **Out of scope** |

---

## 5. Three distinct status planes (do not conflate)

| Plane | Field / source | Meaning |
| --- | --- | --- |
| **Temporal execution status** | `TrialRecord.temporalStatus` from `DescribeWorkflowExecution` (e.g. `COMPLETED`, `RUNNING`, `TERMINATED`, `CANCELED`) | What the Temporal service reports for the workflow execution |
| **Harness trial status** | `TrialRecord.harnessStatus` (`COMPLETED` / `EXCLUDED`, …) | Whether the **runner** accepted the trial as a valid RQ observation after its lifecycle + OWN-P1 |
| **Oracle classification** | `OracleSummary.classification` (four-label set) | DB-primary business outcome; independent of Temporal and harness enums |

**Critical implication for G5-3:** Temporal status `TERMINATED` may still yield a durably persisted harness trial with `harnessStatus=COMPLETED`, **if** R14 quiescence + full `T_observe` + Oracle evaluation + OWN-P1 JSON persistence all succeed. Harness `COMPLETED` means “runner finished the approved observe/Oracle/persist path,” **not** “Temporal workflow completed naturally.” Conversely, Temporal `COMPLETED` without durable JSON is **not** harness `COMPLETED` (OWN-P1).

`terminatedBy` further distinguishes how the harness stopped waiting: `WORKFLOW_STOP` (natural) vs `T_RUN_MAX` (R6 terminate).

---

## 6. G5 acceptance specification

**Profile:** F0 only. **Timing:** `RunnerTiming.protocolDefaults()` only (forbid shorter `--t-*-ms` for acceptance). **Variant:** A.

### Shared preconditions

- Isolated Temporal environment dedicated to this G5 run (Compose lab or equivalent; no shared multi-operator queue use).  
- Postgres `jdbc:postgresql://localhost:5433/business` on allowlist.  
- Temporal reachable (`localhost:7233`), namespace `default`.  
- Exclusive `ExperimentEnvLock` held for the run.  
- Fresh `StateReset` baseline before each trial.  
- Results under an isolated `resultsRoot` (not shared with concurrent pilots).

### Independent runtime scenarios (mandatory)

G5-2 and G5-3 are **separate execution scenarios**. They must not be collapsed into one accidental path.

| Scenario | Worker polling `variant-a-order-fulfillment`? | Expected Temporal terminal status | Expected `terminatedBy` |
| --- | --- | --- | --- |
| **G5-2** | **Yes** — intentional worker + HTTP services up for entire trial | `COMPLETED` (natural) | `WORKFLOW_STOP` |
| **G5-3** | **No** — exclusive empty queue; no worker may poll | `TERMINATED` (after real 120s + R6) | `T_RUN_MAX` |

**Fail-closed cross-contamination rules:**

- A **worker-down / no-worker** incident during an intended **G5-2** run (workflow stays RUNNING → hits `T_run_max` → terminate) is a **failed or invalid G5-2 attempt**. It must **not** be counted as successful G5-2 acceptance, even if G5-3-like artifacts appear.  
- An **unexpected worker** during intended **G5-3** (natural COMPLETED before deadline, **or** `WorkflowTaskStarted` with `eventId` before `WorkflowExecutionTerminated`) yields `g5-3-acceptance.json` = **INVALID**. Preserve immutable `TrialRecord` + history; do **not** count as G5-3 evidence.  
- Successful G5-2 evidence cannot substitute for G5-3, and vice versa.  
- Temporal execution status `RUNNING` alone does **not** prove the workflow was never picked up.  
- Harness `COMPLETED` on `TrialRecord` does **not** imply acceptance `VALID`.

### G5-1 — Real Temporal workflow start

**Setup:** Typically satisfied as the prefix of **G5-2** (worker + services up). May also be observed at the start of G5-3 (start succeeds while RUNNING with no worker).

| # | Assertion | Durable? |
| --- | --- | --- |
| A1 | `VariantAAdapter.start` invoked; Temporal accepts execution | Diagnostic + handle |
| A2 | Returned `runId` non-blank; `workflowId == TrialSpec.workflowId()` (`order-{orderId}`) | **Durable** on trial JSON |
| A3 | `DescribeWorkflowExecution` (same namespace/ids) succeeds while RUNNING or soon after | Diagnostic |
| A4 | Task queue used is `OrderFulfillmentWorkflow.TASK_QUEUE` (`variant-a-order-fulfillment`) | Code fact + diagnostic |
| A5 | Trial record associates `orderId` / `workflowId` / `runId` consistently | **Durable** |

### G5-2 — Natural completion (independent scenario)

**Runtime setup (required):**

1. Isolated Temporal env.  
2. HTTP Inventory/Payment/Shipping up.  
3. Variant A worker **running and polling** `variant-a-order-fulfillment` for the whole trial.  
4. F0 profile; protocol timings unchanged.

| # | Assertion | Notes |
| --- | --- | --- |
| B1 | Temporal status becomes terminal **`COMPLETED`** (natural) | Not `TERMINATED` / `CANCELED` |
| B2 | Harness does **not** call `VariantAAdapter.terminate` | History / spy |
| B3 | `terminatedBy == WORKFLOW_STOP` | Durable |
| B4 | Sequence: start → Temporal COMPLETED → quiesce confirm → **sleep ≥ 30s** `T_observe` → Oracle → persist | Wall clock |
| B5 | `IndependentOracle.evaluate` exactly once | Counter around real JDBC oracle |
| B6 | Oracle classification for successful F0 package = `CONFIRMED_SUCCESS` | Durable; DB-primary |
| B7 | Harness `COMPLETED` **only** after durable JSON (OWN-P1); Temporal COMPLETED alone is insufficient | Status planes §5 |
| B8 | Worker remained available; path did **not** degrade into `T_run_max` terminate | If it did → **invalid G5-2** |

**Not asserted:** inventing success from Temporal status alone without Oracle.

### G5-3 — Forced termination (independent scenario; critical)

#### Operational isolation (fail-closed preconditions — retained)

Before starting the G5-3 trial, the lab/IT **must** establish:

1. **Dedicated isolated Temporal environment** for this run (no concurrent unrelated Variant A workers).  
2. **Exclusive ownership** of task queue `variant-a-order-fulfillment`: **no** other process may intentionally poll it.  
3. Lab checklist / IT preflight confirms worker processes for that queue are **stopped** (console worker, `:orchestration:worker`, leftover IT factories).  
4. If exclusivity **cannot** be established → **exclude / abort G5-3 before `TrialRunner.run`** (do not start the measured trial). Record why.

These controls are **necessary but not sufficient**. Process inspection and a dedicated environment do **not** independently prove that no unexpected worker started processing the workflow task. Temporal **history** (§ below) is the additional evidence check.

No new protocol semantics and no production runner changes are required for these preconditions; they are **lab/IT process controls** plus post-run history inspection.

#### Temporal history evidence (required for valid G5-3)

**Problem:** An unexpected worker may start a Workflow Task, then stall or fail, leaving execution status `RUNNING` until the harness `T_run_max` deadline. Relying on `RUNNING` alone would incorrectly accept that attempt as a “no-worker” R6 experiment.

**Distinction (Temporal concepts):**

| Observation | Meaning | Sufficient for “never picked up”? |
| --- | --- | --- |
| Execution status `RUNNING` | Workflow has not reached a terminal status | **No** — may already have been partially processed |
| Workflow Task **scheduled** without a later start | Task is waiting on the queue; no worker has begun executing it | Compatible with no-worker intent; still not proof alone |
| `WorkflowTaskStarted` in history | A worker **actually started** processing a workflow task | **Disqualifies** G5-3 if it precedes the corresponding termination event (§ ordering rule) |

**API footing in this repo:** console code already calls `GetWorkflowExecutionHistory` and iterates `HistoryEvent` / `EventType` (`ConsoleRunService.loadHistory`). G5-3 lab/IT should use the same class of API against the **exact** measured execution (`workflowId` + `runId`). Event type names below follow Temporal’s protobuf `EventType` vocabulary used by that API (e.g. `EVENT_TYPE_WORKFLOW_TASK_STARTED`, `EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED`); prose may say `WorkflowTaskStarted` / `WorkflowExecutionTerminated`.

##### Event-ordering rule (authoritative for acceptance)

**Disqualifying condition:** any `WorkflowTaskStarted` event belonging to the measured `workflowId` **and** `runId` whose history position is **before** the corresponding `WorkflowExecutionTerminated` event for that same execution.

| Rule | Detail |
| --- | --- |
| Scope | Complete available history of **one** execution: exact `workflowId` + `runId` from the measured trial — never another run or workflow |
| Primary order | Temporal history **`eventId`** (monotonic history order) where available on `HistoryEvent` |
| Do **not** use | Client-side wall-clock of `VariantAAdapter.terminate()` alone — request time and the Temporal `WorkflowExecutionTerminated` event are **not** necessarily simultaneous |
| Pass | `WorkflowExecutionTerminated` found **and** no `WorkflowTaskStarted` with `eventId` **strictly less than** that termination event’s `eventId` |
| Fail closed (INVALID) | Termination event **absent**; history incomplete / truncated; `eventId` ordering cannot be established; history cannot be retrieved; or any earlier `WorkflowTaskStarted` exists |

Operational isolation preflight remains mandatory. History validation **supplements** it; it does **not** replace it. This rule does **not** alter protocol timing, R6/R14, runner behavior, or Oracle semantics.

Practical timing for the check (lab/IT, not a protocol change):

1. After measured R6 terminate + Temporal terminal `TERMINATED`, and after OWN-P1 trial JSON is written (or exclusion JSON if applicable).  
2. Fetch/export history for the measured `workflowId`/`runId`.  
3. Apply the event-ordering rule above.  
4. Write durable **`g5-3-acceptance.json`** (§ acceptance artifact) — **do not** mutate `TrialRecord`.  
5. Retain the history export with the attempt artifacts.

`WorkflowTaskScheduled` without `WorkflowTaskStarted` before `WorkflowExecutionTerminated` is the expected “queued, never picked up” pattern under exclusive empty queue.

#### TrialRecord vs G5-3 acceptance validity (immutability)

| Artifact | Role |
| --- | --- |
| **`TrialRecord` JSON** | What the runner **measured** (OWN-P1). Immutable after write for acceptance purposes. |
| **`g5-3-acceptance.json`** | Whether that run **qualifies as G5-3 evidence**. Separate evaluation. |

- Do **not** rewrite or mutate an existing `TrialRecord`.  
- Do **not** change its `temporalStatus`, `harnessStatus`, `terminatedBy`, Oracle summary, or any other measured fields based on the later acceptance decision.  
- A trial may legitimately have `harnessStatus=COMPLETED` while `g5-3-acceptance.json` records `acceptanceResult=INVALID`.  
- Preserve original trial JSON and Temporal history diagnostics alongside the acceptance artifact.

#### Durable acceptance artifact: `g5-3-acceptance.json`

**Documentation requirement** (to be produced by lab/IT after D-AUTH-G5; not a production runner output today). Suggested path: `experiment/results/<expId>/acceptance/g5-3-acceptance.json` (or next to the history export).

Minimum fields:

| Field | Meaning |
| --- | --- |
| `scenarioId` | `"G5-3"` |
| `workflowId` | Measured workflow id |
| `runId` | Measured run id |
| `gitSha` | Code tip under test |
| `preflightResult` | Pass/fail of operational isolation |
| `isolationDiagnostics` | Relevant notes (env, queue, worker processes checked) |
| `historySource` | How history was obtained (e.g. `GetWorkflowExecutionHistory`) |
| `historyArtifactPath` | Path to retained history export |
| `historyComplete` | Whether the relevant history was complete enough to evaluate |
| `workflowExecutionTerminatedFound` | Whether corresponding `WorkflowExecutionTerminated` was found |
| `workflowTaskStartedBeforeTermination` | Whether any `WorkflowTaskStarted` has `eventId` &lt; termination `eventId` |
| `eventOrderingValidation` | Pass/fail of the § ordering rule (include termination/`WorkflowTaskStarted` `eventId`s when known) |
| `acceptanceResult` | `VALID` \| `INVALID` |
| `invalidationReason` | Explicit reason when `INVALID` (else null/omitted) |
| `evaluatedAt` | Timestamp of the acceptance evaluation |

#### Invalidation behavior (history / contamination)

| Condition | Action |
| --- | --- |
| Any `WorkflowTaskStarted` with `eventId` **before** `WorkflowExecutionTerminated` | **INVALID** in `g5-3-acceptance.json`. Preserve `workflowId`, `runId`, history export, **and** immutable `TrialRecord`. Do **not** reinterpret as valid no-worker G5-3. Do **not** change protocol/Oracle/`TrialRecord`. |
| Unexpected natural `COMPLETED` before deadline | **INVALID** (same preservation). |
| Termination event absent, history incomplete, ordering inconclusive, or history not retrieved | **Fail closed → INVALID**. Preserve whatever ids/diagnostics exist. |
| Preflight isolation failed | Abort **before** measured trial (unchanged). |

#### Contract sequence (must preserve)

1. Operational isolation preflight (§ above); abort before `TrialRunner.run` if unmet.  
2. Trial `t0`; deadline `t0 + 120s` (real wall clock).  
3. Workflow remains Temporal `RUNNING` until that deadline under intended no-poller setup.  
4. After deadline: re-`describe` race (G4).  
5. If still RUNNING → `terminate("T_run_max")` (**not** cancel). Measured R6 — not cleanup.  
6. Quiesce wait (default 5s) until Temporal terminal (`TERMINATED` expected).  
7. If not quiesced → `EXCLUDED`/`WORKFLOW_NOT_QUIESCED`; **block** next trial; **no Oracle**.  
8. If quiesced → **full** `T_observe = 30s`.  
9. Oracle **once** → OWN-P1 persist (`TrialRecord` written; thereafter immutable for acceptance).  
10. **History + acceptance artifact:** apply event-ordering rule; write `g5-3-acceptance.json` (`VALID`/`INVALID`) **without** mutating `TrialRecord`.  
11. Harness may show `COMPLETED` + Temporal `TERMINATED` + `terminatedBy=T_RUN_MAX` even when acceptance is `INVALID` — both truths stand.

**Forbidden:** shortening `T_run_max` / `T_observe`; stub observe sleeps; `cancel`; changing activity/workflow timeouts to speed the test; counting a worker-down G5-2 accident as G5-3 (or the reverse); treating `RUNNING` alone as proof of no worker pickup; using client `terminate()` call time as the sole ordering key; mutating `TrialRecord` to reflect acceptance.

#### Technique (no contract change)

> Start the workflow via the runner while **no** worker polls `variant-a-order-fulfillment`. The execution remains Temporal `RUNNING` until real `T_run_max`, then R6 terminate. **Validate** with Temporal history (`eventId` order): no `WorkflowTaskStarted` before `WorkflowExecutionTerminated` for that `workflowId`/`runId`. Record result in `g5-3-acceptance.json` without mutating `TrialRecord`.

| Property | Assessment |
| --- | --- |
| Exercises real `WorkflowStub.terminate` | Yes |
| Preserves 120s + 5s + 30s (~155s+) | Yes |
| Avoids ActivityPolicies / workflow edits | Yes |
| Empty DB → Oracle ≠ `CONFIRMED_SUCCESS` | Expected; record actual classification |
| Stray / stalled worker pickup | Detected via history `eventId` rule; fail-closed → `INVALID` acceptance |

**Rejected without Owner contract change:** shortened timings; production `Workflow.sleep`; test-only activity timeout raises.

#### G5-3 assertions

| # | Assertion |
| --- | --- |
| C0 | Operational isolation preflight passed; no intentional poller at trial start |
| C1 | Wall-clock ≥ ~120s from `t0` before R6 terminate call |
| C2 | Measured `terminate("T_run_max")`; Temporal status `TERMINATED` (not `CANCELED`) |
| C3 | `terminatedBy == T_RUN_MAX` on durable **`TrialRecord`** (unchanged by acceptance) |
| C4 | Full `T_observe` after quiesce before Oracle |
| C5 | Oracle exactly once; DB snapshot + trial metadata only |
| C6 | Classification not forced to `CONFIRMED_FAILURE` by terminate alone (D-011); record actual snapshot-driven label |
| C7 | OWN-P1 may yield harness `COMPLETED` with Temporal `TERMINATED` — recorded on `TrialRecord` |
| C8 | History for exact `workflowId`/`runId`: `WorkflowExecutionTerminated` found; no earlier `WorkflowTaskStarted` by **`eventId`** |
| C9 | Durable `g5-3-acceptance.json` written; `TrialRecord` **not** mutated |
| C10 | If C8 fails / history missing / inconclusive → `acceptanceResult=INVALID` (fail closed); ids + history + trial JSON preserved |
| C11 | Unexpected early natural COMPLETED → `INVALID` acceptance (diagnostics kept) |

### G5-4 — Independent Oracle

| # | Assertion |
| --- | --- |
| D1 | Exactly one `evaluate` per successful quiesced trial (G5-2 and G5-3 each) |
| D2 | `OracleSnapshotRepository.load(orderId, sku)` used (JDBC); classifier sees `BusinessSnapshot` only |
| D3 | Temporal status / HTTP / orchestrator labels are **not** classifier inputs |
| D4 | Oracle runs only after quiesce + full `T_observe` |
| D5 | Persisted `OracleSummary` not recomputed on CSV rebuild; persist-fail keeps oracle on `.partial` / EXCLUDED (OWN-P1) |

---

## 7. Lifecycle diagrams

### G5-2 — natural path (worker polling)

```text
[preflight: worker + services UP]
reset → arm F0 → verify → start
  → awaitStop (Temporal COMPLETED before deadline)
  → terminatedBy=WORKFLOW_STOP
  → awaitStop(quiesce)
  → sleep(T_observe=30s)
  → Oracle once → saveTrial harness COMPLETED
  → disarm
  → [cleanup: must not rewrite measured Temporal COMPLETED]
```

### G5-3 — forced path (no worker polling)

```text
[preflight: dedicated env + queue exclusivity — NO pollers; else abort]
reset → arm F0 → verify → start
  → awaitStop until t0+120s (still RUNNING)   // RUNNING alone ≠ no pickup
  → describe race
  → terminate("T_run_max")     // measured R6 — not cleanup
  → awaitStop(quiesceWait)     // Temporal TERMINATED
  → sleep(T_observe=30s)       // R14
  → Oracle once → OWN-P1 TrialRecord (immutable thereafter for acceptance)
  → disarm
  → HISTORY GATE (eventId): no WorkflowTaskStarted before WorkflowExecutionTerminated
  → write g5-3-acceptance.json VALID|INVALID (do not mutate TrialRecord)
  → [cleanup: see §8 — distinguishable from R6]
```

---

## 8. Cleanup boundaries

| Rule | Requirement |
| --- | --- |
| Measured R6 terminate | The G5-3 `terminate("T_run_max")` inside `TrialRunner` **is part of the measured lifecycle**. Evidence (ids, Temporal `TERMINATED`, `terminatedBy=T_RUN_MAX`, timings, history export) must be captured **before** any post-trial cleanup mutates that execution further. |
| No silent pre-evidence cleanup | Cleanup must **not** silently terminate, cancel, or reset the measured execution **before** durable trial JSON, history export, and `g5-3-acceptance.json` for that attempt are written. |
| Distinguishable cleanup terminate | Any **post-evidence** Temporal terminate must be clearly separate from protocol R6 (different reason / logged as `cleanup`). Never present cleanup terminate as G5-3 acceptance; cleanup must not invent a substitute `WorkflowExecutionTerminated` story for the measured run. |
| Preserve leftovers | Retain `workflowId`, `runId`, Temporal status, history, acceptance artifact, and operator notes. Prefer diagnostics over destructive wipe until evidence is secured. |
| `TrialRecord` immutability | Cleanup / acceptance evaluation must **never** rewrite measured trial JSON fields. |
| StateReset scope | DB `StateReset` is for business schemas between trials; it does **not** replace Temporal cleanup and must not hide invalid G5-2/G5-3 contamination. |
| Env lock | Release lock after the scenario finishes (including acceptance artifact write); do not drop lock while measured await/terminate/observe is in flight. |

---

## 9. Environment assessment

### Compose (`infra/docker-compose.yml`) — inspected

| Component | Fact |
| --- | --- |
| Postgres | Host `5433`; init scripts; used by allowlist lab URL |
| Temporal | `temporalio/auto-setup:1.25.2` → `7233`; UI `8088` |
| Namespace | Client default `default` (`TemporalSettings`) |
| Task queue | `variant-a-order-fulfillment` (code constant) |
| Worker | **Not** in Compose — must run `:orchestration:worker` or embedded `VariantAWorkers` |
| Services | **Not** in Compose — boot jars / local Spring apps |

### Recommendation

**Compose Temporal + Postgres is sufficient for G5** if treated as a **dedicated isolated environment** for the run, with:

1. Fail-closed operational queue exclusivity for G5-3 (§6) **plus** post-R6 Temporal history gate (`WorkflowTaskStarted`).  
2. Explicit G5-2 boot order: infra → Postgres → Temporal → services → **worker up** → runner IT.  
3. Explicit G5-3 boot order: infra → Postgres → Temporal → **worker down / no pollers** → runner IT → history export/validation (services optional for empty-DB Oracle).  
4. Isolated `resultsRoot` + `envId` + allowlist.  
5. Cleanup per §8.  
6. Opt-in execution (`THESIS_RUN_G5` / `@EnabledIf`); fail-hard when required — never silent skip.

**Dedicated multi-tenant cluster:** not required for G5 if Compose isolation holds.

---

## 10. Failure matrix (existing contract)

| Failure | Harness status / reason | Oracle? | Next trial? | Retained evidence | G5 acceptance impact |
| --- | --- | --- | --- | --- | --- |
| Unsafe env / allowlist | `EXCLUDED` / gate reason | No | — | Exclusion JSON | Invalid run |
| Env lock busy | `ENV_BUSY` | No | N/A | — | Abort |
| G5-3 queue exclusivity fail | Abort **before** trial | No | N/A | Preflight log | **No G5-3 start** |
| StateReset / JDBC gate | `EXCLUDED` | No | Usually yes | Exclusion JSON | Invalid trial |
| Start fails | `EXCLUDED` / `HARNESS_CRASH` | No | Yes* | Exclusion | Invalid |
| **Worker down during intended G5-2** | May become terminate path | Maybe | * | Trial JSON | **Invalid G5-2** (do not rebadge as G5-3 success) |
| **Stray worker / WorkflowTaskStarted before WorkflowExecutionTerminated** | Runner may still COMPLETE harness | Maybe | * | Immutable `TrialRecord` + history + `g5-3-acceptance.json`=`INVALID` | Not G5-3 evidence |
| **G5-3 history missing / inconclusive / no termination event** | N/A (acceptance gate) | — | * | Partial diagnostics + acceptance=`INVALID` | Fail closed |
| Natural G5-2 OK | Harness `COMPLETED`; Temporal `COMPLETED` | Yes once | Yes | Trial JSON | Valid G5-2 |
| G5-3 R6 + OWN-P1 + history gate OK | Harness may be `COMPLETED`; Temporal `TERMINATED`; acceptance=`VALID` | Yes once | Yes | Trial JSON + history + `g5-3-acceptance.json` | Valid G5-3 evidence |
| Terminate fails & still RUNNING | `EXCLUDED` / `TEMPORAL_TERMINATE_FAILED` | No | Yes* | Exclusion + ids | Invalid G5-3 |
| Not quiesced | `EXCLUDED` / `WORKFLOW_NOT_QUIESCED` | **No** | **Blocked** | Exclusion JSON | Invalid; cleanup per §8 |
| Oracle SQL failure | `EXCLUDED` / `ORACLE_FAILURE` | Failed | Yes* | Exclusion | Invalid |
| Persist COMPLETED fails | `EXCLUDED` / `PERSISTENCE_FAILURE` + `.partial` | Kept | Yes* | `.partial` | Invalid harness COMPLETED; oracle preserved (OWN-P1) |
| Disarm throws | `blockFurtherTrials` | — | **Blocked** | Prior artifact | Scenario aborted |
| Lock release fail | Best-effort | — | May ENV_BUSY | Lock file | Manual |

\* Unless `blockFurtherTrials` or disarm failure. No new enum values.

---

## 11. Required code / artifact changes (after D-AUTH-G5 only)

### Production / contract

| Area | Expected change |
| --- | --- |
| `TrialRunner`, timings, Oracle, protocol, terminate semantics | **None** |
| Workflow / ActivityPolicies | **None** |

### Test / lab wiring (authorized under D-AUTH-G5)

| Component | Likely work |
| --- | --- |
| New IT under `:experiment:runner` | **Separate** G5-2 and G5-3 methods/scenarios; long timeouts; opt-in gate |
| Process helpers | Boot services + worker for G5-2; ensure **no** pollers for G5-3; queue exclusivity preflight |
| History + acceptance helper (test/lab only) | Fetch history for `workflowId`/`runId`; `eventId` ordering vs `WorkflowExecutionTerminated`; write `g5-3-acceptance.json`; never mutate `TrialRecord` |
| Optional thin test spy | Count measured `terminate` / forbid cancel; count Oracle calls |
| Docs / README lab section | Independent scenarios, isolation checklist, cleanup §8, `THESIS_RUN_G5` |
| CI | Optional non-merge job **or** local-only — Owner decision |

### Explicit non-changes

`T_run_max=120s` · `T_observe=30s` · R6/R14 · Oracle classifier / four-label set · OWN-P1 · S4-D · Variant C / TH-303 · `EXPERIMENT_LOG.md` · `NEXT_TASK` auth state · Taiga #37 Done · production lifecycle code.

---

## 12. Test execution plan (do not run in this discovery task)

```bash
# Infra
cd infra && docker compose up -d

# Opt-in G5 (proposed name; finalize in impl auth)
export THESIS_RUN_G5=true
export TEMPORAL_TARGET=localhost:7233
export VARIANT_A_TEST_JDBC_URL=jdbc:postgresql://localhost:5433/business
# … service URLs / allowlist as in runner README …

# After D-AUTH-G5 implementation:
./gradlew :experiment:runner:test --tests '*RunnerLiveTemporalG5IT*'
```

Approximate wall clock: G5-2 ~40–90s; G5-3 ~160–200s; scenarios run **separately**.

Manual CLI F0 lab remains optional diagnostics — **acceptance SoT = G5 IT artifacts**, not ad-hoc CLI alone.

---

## 13. Evidence / artifact schema

| Artifact | Location | Required |
| --- | --- | --- |
| Git SHA | trial `gitSha` + acceptance `gitSha` | Yes |
| Scenario id | Explicit `G5-2` vs `G5-3` | Yes |
| Env + isolation note | Preflight in acceptance / report | Yes (esp. G5-3) |
| **Trial JSON (`TrialRecord`)** | `experiment/results/<expId>/trials/*.json` | Yes — **immutable** measured record (OWN-P1) |
| Status plane fields on trial | `temporalStatus`, `harnessStatus`, `terminatedBy`, `oracle.classification` | Yes — not rewritten by acceptance |
| **G5-3 Temporal history export** | Retained path referenced by acceptance | **Yes** for G5-3 evaluation |
| **`g5-3-acceptance.json`** | e.g. `…/acceptance/g5-3-acceptance.json` | **Yes** for G5-3 — `VALID`/`INVALID`; separate from trial |
| Invalid-attempt diagnostics | ids + history + acceptance reason | Yes when `INVALID` |
| Disclaimer | ≠ S4-D ≠ EXP-* confirmatory; harness COMPLETED ≠ acceptance VALID | Yes |
| `EXPERIMENT_LOG` row | **Owner decision** — out of scope for this doc edit | Unchanged / pending Owner |

**Consistency:** OWN-P1 governs when harness may claim `COMPLETED`. G5-3 **acceptance** is a later, separate gate over history + isolation and must not violate OWN-P1 immutability of durable trial JSON.

---

## 14. Effort estimate

| Slice | SP (indicative) | Assumptions |
| --- | --- | --- |
| G5 IT harness + isolation preflight | 1.5–2.5 | Separate G5-2/G5-3 wiring |
| G5-2 natural path | 0.5–1 | Worker up; real 30s observe |
| G5-3 no-poller terminate path | 0.5–1 | Real ~155s; fail-closed exclusivity |
| Docs / handoff / CI opt-in | 0.5 | |
| **Total** | **~3–5 SP** | |

---

## 15. Open Owner decisions (genuine)

1. **Issue D-AUTH-G5 now?**  
2. **Accept no-poller technique for G5-3** with operational isolation **plus** history `eventId` gate (no `WorkflowTaskStarted` before `WorkflowExecutionTerminated`) and separate `g5-3-acceptance.json`? (recommended)  
3. **CI:** optional long Actions job vs local-lab only?  
4. **Index G5** in `EXPERIMENT_LOG` (non-EXP) or omit until EXP-*?  
5. After G5: **S4-D** vs methodology/writing?

---

## 16. Readiness verdict (for Owner)

| Verdict | Meaning |
| --- | --- |
| **Ready for D-AUTH with explicit decisions** | After Owner accepts §15 (esp. G5-3 isolation technique) and grants **D-AUTH-G5** |

**No remaining technical/contractual blocker** that requires changing `T_*`, R6/R14, Oracle, OWN-P1, or production runner lifecycle. G5-3 specifies operational isolation, **`eventId`-ordered** history validation against `WorkflowExecutionTerminated`, and a **separate** durable acceptance artifact. Remaining work is test/lab wiring under a separate D-AUTH-G5.

**Still blocked without Owner choice:** shortened timings; treating contaminated / history-inconclusive runs as valid G5-3 evidence; mutating `TrialRecord` to encode acceptance.

**Genuine residual uncertainty (non-blocking for D-AUTH wording):** exact protobuf enum constant spelling in the chosen SDK binding will be confirmed at implementation against `io.temporal.api.enums.v1.EventType` (repo already uses this enum family via `ConsoleRunService`); acceptance logic must map to the same history API rather than invent alternate event models.

---

## 17. PM boundaries (unchanged)

| Action | Now |
| --- | --- |
| This document | Allowed (this edit) |
| Implement / execute G5 | **No** until D-AUTH-G5 |
| S4-D / F1–F3 live / Variant C / TH-303 | **No** |
| Mark #37 Done | **No** |
| EXP-* / `EXPERIMENT_LOG` edits | **No** (this task) |
| `NEXT_TASK` | remains **`awaiting_owner`** |
