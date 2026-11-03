# 09 — Test catalogue

## Levels

| Level | How Temporal/DB appear | Example classes |
| --- | --- | --- |
| **Unit** | Mocks / pure functions | `OutcomeClassifier` via ScenarioClassificationTest pieces, `G5OptInTest`, `ExpectedCellOutcomeTest`, `G5HistoryGateTest` |
| **Integration (service)** | Real Spring + DB (Testcontainers/local) | `InventoryIntegrationTest`, `PaymentIntegrationTest`, `ShippingIntegrationTest` |
| **JDBC IT (oracle/runner)** | Real DB | `OracleJdbcIT`, `StateResetJdbcIT`, `ExperimentEnvLockIT` |
| **CompIT** | `TestWorkflowEnvironment` | `OrderFulfillmentHappyPathIT`, `OrderFulfillmentCompensationIT`, retry/lost-id tests |
| **Console real Temporal E2E** | Real server, gated | `ConsoleRealTemporalE2EIT` + `ConsoleTemporalE2EGate` |
| **G5 live Temporal IT** | Real server, **opt-in env** | `RunnerLiveTemporalG5IT` |
| **Manual / EXP-*** | Campaign execution | **None recorded** |

CI green ⇒ unit/CompIT/integration as configured in workflows — **not** proof of live G5 or EXP-*.

## Selected automated tests

| Test class / method | Level | Verifies | Important assertion | Does NOT prove |
| --- | --- | --- | --- | --- |
| `OrderFulfillmentHappyPathIT` | CompIT | F0-like saga | Terminal success states | Real poller exclusivity |
| `OrderFulfillmentCompensationIT` | CompIT | Compensation paths | Refund/release on failures | Harness TrialRecord schema |
| `OrderFulfillmentRetryTimeoutTest` | CompIT | Retry/timeout policies | Non-retry BUSINESS | Lab ENV lock |
| `OrderFulfillmentLostIdRecoveryTest` | CompIT | Lost create-id recovery | Single recovery attempt semantics | F3 opacity |
| `ScenarioClassificationTest` | Unit/classifier | Label mapping | SUCCESS/FAILURE/AMBIGUOUS cases | Live JDBC flakiness |
| `IndependenceTest` | Unit | No Temporal/LLM coupling | Classification from snapshot only | Runner lifecycle |
| `OracleJdbcIT` | JDBC IT | Snapshot+classify on DB | Real rows → labels | Multi-tenant isolation under load |
| `TrialRunnerLifecycleTest` | Runner unit/IT w/ mocks or stubs | F0 lifecycle | COMPLETED path | Real Temporal |
| `TrialRunnerF1PLifecycleTest` | Runner | F1-P path | Expected failure classification wiring | Payment prod chaos |
| `TrialRunnerF2LifecycleTest` | Runner | F2 path | Failure + compensation wiring | — |
| `TrialRunnerF3LifecycleTest` | Runner | F3 recovered/unresolved | AMBIGUOUS gate path | Live opacity races |
| `F0/F1P/F2/F3FaultControllerTest` | Unit | Arm/verify contracts | Fail-closed verify | — |
| `F3UnresolvedAcceptanceGateTest` | Unit | Extra gate | Gate required for profile | — |
| `FileResultStoreTest` | Unit | Immutability + round-trip | Durable equality after save/read | — |
| `MatrixExperimentDriverTest` | Unit | Matrix schedule | Summary aggregation | EXP statistics |
| `G5OptInCiSafetyTest` | Unit | Default off | No live without flags | — |
| `DescribeTaskQueueG5QueuePollerProbeTest` | Unit | Probe semantics | Error ⇒ treat as pollers | — |
| `G5HistoryFetcherTest` / `G5HistoryGateTest` | Unit | Pagination + eventId | Incomplete history ≠ pass | Live server quirks |
| `G5AcceptanceWriterTest` | Unit | VALID-only write | No VALID file on EXCLUDED | — |
| `G5OracleExactlyOnceViaTrialRunnerTest` | Unit | Oracle once | Counter = 1 | Live double-call races |
| `G5ScenarioContractTest` | Unit | G5-2≠G5-3; terminate≠cancel | Independent selection | Live |
| `RunnerLiveTemporalG5IT` | G5 live | G5-1…4 when opted in | Per-scenario asserts | **Not run in default CI**; not EXP-* |
| `ConsoleRealTemporalE2EIT` | Real E2E gated | Console path | — | G5-3 acceptance |

## G5 scenario prerequisites & artifacts

| Scenario | Prerequisites | Evidence artifacts |
| --- | --- | --- |
| G5-1 | `THESIS_RUN_G5`, dedicated ENV, Temporal up | Workflow id/run id from start |
| G5-2 | + worker polling queue, scenario G5-2 | COMPLETED TrialRecord, WORKFLOW_STOP, Oracle |
| G5-3 | + **no** pollers on queue, scenario G5-3, 120s+observe wall time | TrialRecord + history validation + `g5-3-acceptance.json` iff VALID |
| G5-4 | Exercised via TrialRunner Oracle-once tests / live path | Call counter / single Oracle summary |

## Test case specs (human-readable)

### TC-F0-HAPPY
- **Objective:** Baseline success classification  
- **Preconditions:** ENV safe; DB allowlisted; services+worker up (for live); else CompIT  
- **Input:** Profile F0  
- **Steps:** TrialRunner lifecycle  
- **Expected Oracle:** `CONFIRMED_SUCCESS`  
- **Evidence:** TrialRecord COMPLETED  
- **Automated:** HappyPathIT + TrialRunnerLifecycleTest  
- **Execution status:** CompIT/CI exercised; full harness live matrix **not** claimed as EXP-*

### TC-F1P-CHARGE-FAIL
- **Objective:** Payment fail after hold → compensated failure  
- **Fault:** `CHARGE_FAIL`  
- **Expected Oracle:** `CONFIRMED_FAILURE`  
- **Automated:** TrialRunnerF1PLifecycleTest, F1PFaultControllerTest  
- **Status:** automated tests on CI; not EXP-*

### TC-F2-ARRANGE-REJECT
- **Objective:** Capture then shipping reject → refund+release  
- **Expected Oracle:** `CONFIRMED_FAILURE`  
- **Automated:** TrialRunnerF2LifecycleTest  
- **Status:** CI tests; not EXP-*

### TC-F3-RECOVERED
- **Objective:** Lost charge response recovers via resolve  
- **Expected Oracle:** `CONFIRMED_SUCCESS`  
- **Automated:** TrialRunnerF3LifecycleTest (recovered)  
- **Status:** CI tests; not EXP-*

### TC-F3-UNRESOLVED
- **Objective:** Opaque resolve leaves AMBIGUOUS  
- **Expected Oracle:** `AMBIGUOUS` + unresolved gate  
- **Automated:** F3 lifecycle + F3UnresolvedAcceptanceGateTest  
- **Status:** CI tests; not EXP-*

### TC-G5-3-TERMINATE (proposed live)
- **Objective:** No-poller R6 terminate evidence  
- **Preconditions:** Owner live auth; flags; empty pollers  
- **Expected:** Temporal TERMINATED; harness may COMPLETED; acceptance VALID only if history gate passes  
- **Automated live:** RunnerLiveTemporalG5IT (opt-in)  
- **Status:** **not executed** as authorized live evidence in this documentation session

Cases without CI/live artifacts are labelled **proposed** for live execution only above.
