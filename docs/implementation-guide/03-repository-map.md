# 03 — Repository map

## Module / package table

| Module / package | Responsibility | Important classes | Depends on | Tests |
| --- | --- | --- | --- | --- |
| `:contracts` | Shared DTOs, state enums, Oracle `BusinessOutcome` | `BusinessOutcome`, payment/inventory/shipping states, request DTOs | — | `DtoValidationTest`, transition tests |
| `:services:inventory` | Stock / reservations HTTP API | Controllers, domain services, schema | contracts | `InventoryIntegrationTest` |
| `:services:payment` | Charge / resolve / refund + fault headers | Controllers, F3 lost-response behavior | contracts | `PaymentIntegrationTest`, `PaymentResolveRulesTest` |
| `:services:shipping` | Arrange / cancel + `ARRANGE_REJECTED` | Controllers | contracts | `ShippingIntegrationTest` |
| `:orchestration:platform` | Shared Temporal/Spring wiring helpers | platform utilities | — | (module tests if present) |
| `:orchestration:variant-a` | Variant A workflow + activities + policies | `OrderFulfillmentWorkflow` / `OrderFulfillmentWorkflowImpl`, `OrderFulfillmentActivities` / `HttpOrderFulfillmentActivities`, `ActivityPolicies`, `VariantAWorkers` | contracts, platform | CompIT suite (`*HappyPathIT`, `*CompensationIT`, retry/lost-id tests) |
| `:orchestration:worker` | Spring Boot Temporal worker process | `VariantAWorkerApplication` | variant-a | via CompIT / E2E |
| `:orchestration:console` | Manual Temporal start UI/API | `ConsoleRunService` | variant-a | `ConsoleRealTemporalE2EIT` (gated) |
| `:experiment:oracle` | Independent snapshot + classifier | `IndependentOracle`, `OutcomeClassifier`, `OracleSnapshotRepository` | contracts | `ScenarioClassificationTest`, `IndependenceTest`, `OracleJdbcIT` |
| `:experiment:runner` | Trial harness, faults, matrix, G5 lab | `TrialRunner`, `VariantAAdapter`, `FaultController*`, `FileResultStore`, `g5/*` | oracle, variant-a | Lifecycle tests, fault tests, G5 unit/IT |
| `infra/` | Compose / Postgres / Temporal lab layout | docker assets | — | manual / ENV docs |
| `coordination/` | Project SoT for decisions/tasks | `PROJECT_STATE`, `DECISION_LOG`, … | — | n/a |

## Concept → code navigation

| Concept | Class / method | File path | Tests | Explanation |
| --- | --- | --- | --- | --- |
| Order fulfillment saga | `OrderFulfillmentWorkflowImpl.fulfill` | `orchestration/variant-a/.../OrderFulfillmentWorkflowImpl.java` | HappyPathIT, CompensationIT | reserve→charge→arrange→consume + compensation |
| Task queue name | `OrderFulfillmentWorkflow.TASK_QUEUE` | `.../OrderFulfillmentWorkflow.java` | G5 IT, CompIT | `variant-a-order-fulfillment` |
| Activity retries | `ActivityPolicies.defaultOptions` | `.../ActivityPolicies.java` | `ActivityPoliciesTest`, RetryTimeoutTest | 5 attempts; no retry on BUSINESS |
| Lost-id recovery | `ActivityPolicies.lostIdRecoveryOptions` | same | `OrderFulfillmentLostIdRecoveryTest` | maxAttempts=1 recovery POST |
| Start trial workflow | `VariantAAdapter.start` | `experiment/runner/.../VariantAAdapter.java` | lifecycle tests, FaultHintTest | maps fault hints into workflow input |
| Trial lifecycle | `TrialRunner.run` | `.../lifecycle/TrialRunner.java` | `TrialRunnerLifecycleTest`, F1P/F2/F3 lifecycle | reset→arm→…→Oracle→persist |
| Canonical profiles | `CanonicalFaultProfiles.requireCanonical` | `.../fault/CanonicalFaultProfiles.java` | controller tests | rejects bare `F1`/`F3` |
| F1-P injection | `F1PFaultController.arm/verify` | `.../F1PFaultController.java` | `F1PFaultControllerTest`, F1P lifecycle | `CHARGE_FAIL` on payment |
| F2 injection | `F2FaultController` | `.../F2FaultController.java` | F2 tests | `ARRANGE_REJECTED` on shipping |
| F3-Recovered | `F3RecoveredFaultController` | `.../F3RecoveredFaultController.java` | F3 tests | lost charge response; resolve visible |
| F3-Unresolved | `F3UnresolvedFaultController` | `.../F3UnresolvedFaultController.java` | F3 + acceptance gate | + `RESOLVE_TRANSPORT_OPAQUE` |
| Expected matrix cell | `ExpectedCellOutcome.forProfile` | `.../accept/ExpectedCellOutcome.java` | `ExpectedCellOutcomeTest` | harness expected Oracle label |
| F3-Unresolved harness gate | `F3UnresolvedAcceptanceGate.rejectReason` | `.../accept/F3UnresolvedAcceptanceGate.java` | `F3UnresolvedAcceptanceGateTest` | post-Oracle EXCLUDE if gate fails |
| HTTP activities | `HttpOrderFulfillmentActivities` | `orchestration/variant-a/.../HttpOrderFulfillmentActivities.java` | CompIT | REST + fault headers |
| Oracle classify | `OutcomeClassifier.classify` | `experiment/oracle/.../OutcomeClassifier.java` | ScenarioClassificationTest | four-label Option C |
| Oracle evaluate | `IndependentOracle.evaluate` | `.../IndependentOracle.java` | OracleJdbcIT, IndependenceTest | JDBC snapshot then classify |
| Persist evidence | `FileResultStore.saveTrial` | `.../store/FileResultStore.java` | `FileResultStoreTest` | immutable COMPLETED rules |
| Matrix driver | `MatrixExperimentDriver` | `.../MatrixExperimentDriver.java` | `MatrixExperimentDriverTest` | multi-profile schedule |
| DB baseline | `StateReset.resetToProtocolBaseline` | `.../reset/StateReset.java` | `StateResetJdbcIT` | per-SKU isolation |
| ENV safety | `ExperimentEnvLock` / JDBC allowlist | runner env package | `ExperimentEnvLockIT`, `JdbcAllowlistTest` | fail-closed destructive ops |
| G5 opt-in | `G5OptIn` | `.../g5/G5OptIn.java` | `G5OptInTest`, CiSafetyTest | `THESIS_RUN_G5` etc. |
| G5 queue exclusivity | `DescribeTaskQueueG5QueuePollerProbe` | `.../g5/DescribeTaskQueueG5QueuePollerProbe.java` | probe tests | fail-closed on API error |
| G5-3 history gate | `G5HistoryGate` / `G5HistoryFetcher` | `.../g5/` | History*Test | pagination + eventId order |
| G5-3 acceptance | `G5AcceptanceEvaluator` / Writer | `.../g5/` | Acceptance*Test | writes `g5-3-acceptance.json` |
| Live G5 scenarios | `RunnerLiveTemporalG5IT` | `.../g5/RunnerLiveTemporalG5IT.java` | same (opt-in) | G5-1…G5-4; not default CI |

## Important class cards (condensed)

### `TrialRunner`
**In one sentence:** Orchestrates one experimental trial end-to-end and persists a durable `TrialRecord`.  
**Important methods:** `run(TrialSpec)` — full lifecycle; private `persistExcluded` / completed persist paths.  
**Calls:** `StateReset`, `FaultController`, `VariantAdapter`, `IndependentOracle`, `FileResultStore`.  
**Failure cases:** verify fail, terminate fail, non-quiesced, Oracle SQLException → EXCLUDED / abort policies.  
**Tests:** `TrialRunnerLifecycleTest`, `TrialRunnerF1PLifecycleTest`, `TrialRunnerF2LifecycleTest`, `TrialRunnerF3LifecycleTest`, `G5OracleExactlyOnceViaTrialRunnerTest`.

### `OrderFulfillmentWorkflowImpl`
**In one sentence:** Deterministic Temporal saga for Variant A order fulfillment with compensation.  
**Important methods:** `fulfill(OrderFulfillmentInput)` — forward path + catch compensation.  
**Depends on:** activity stubs with `ActivityPolicies`.  
**Tests:** HappyPathIT, CompensationIT, RetryTimeoutTest, LostIdRecoveryTest, WorkflowOrderTest.

### `IndependentOracle`
**In one sentence:** Loads a business snapshot over JDBC and classifies without workflow/LLM inputs.  
**Important methods:** `evaluate(OracleEvaluationRequest)`, `evaluateSnapshot(...)`, `classifierOnly()`.  
**Tests:** OracleJdbcIT, IndependenceTest, ScenarioClassificationTest.

### `OutcomeClassifier`
**In one sentence:** Pure function from `BusinessSnapshot` to `BusinessOutcome` + pattern/reason.  
**Important method:** `classify(BusinessSnapshot)`.  
**Labels:** four-value enum including `INVALID` for invariant violations.

### `VariantAAdapter`
**In one sentence:** Harness bridge from `TrialSpec` + armed fault to Temporal start/await/describe/terminate.  
**Important methods:** `start`, `awaitStop`, `describe`, `terminate`, `readWorkflowOutcome`.  
**Tests:** lifecycle (via runner), `VariantAAdapterFaultHintTest`.

### `FileResultStore`
**In one sentence:** Durable file persistence with immutability rules for terminal artifacts.  
**Important methods:** `saveTrial`, `readTrial`, `hasTerminalArtifact`.  
**Tests:** `FileResultStoreTest` (incl. round-trip equality).

### G5 helpers
See [10-g5-live-readiness.md](10-g5-live-readiness.md) for `G5OptIn`, probes, history gate, acceptance writer, `RunnerLiveTemporalG5IT`.
