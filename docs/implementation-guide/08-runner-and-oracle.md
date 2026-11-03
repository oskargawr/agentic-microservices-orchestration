# 08 — Runner and Oracle

## TrialRunner

Owns one trial. See sequence in [04-runtime-lifecycle.md](04-runtime-lifecycle.md).

**Data in:** `TrialSpec` (experimentId, trialId, faultProfileId, orderId, businessInputs, …).  
**Data out:** durable `TrialRecord` (`schemaVersion=th-402-phase1-1`).

### TrialRecord fields (selected)

| Field | Meaning |
| --- | --- |
| `temporalStatus` | Status string from Temporal describe/stop |
| `harnessStatus` | `COMPLETED` / `EXCLUDED` / `ABORTED_EXPERIMENT` |
| `terminatedBy` | `WORKFLOW_STOP` or `T_RUN_MAX` (R6) |
| `oracle` | `OracleSummary` snapshot of classification |
| `includeInRqRates` | false for excluded |
| `workflowQuiesced` | Temporal terminal after quiesce wait |

**Immutability:** `FileResultStore` refuses downgrading/replacing COMPLETED improperly; terminal artifacts block duplicate attempts (`DUPLICATE_TRIAL_ATTEMPT`).

## HarnessStatus vs Temporal vs G5-3 VALID

| Signal | Owner |
| --- | --- |
| `temporalStatus=TERMINATED` | Temporal execution |
| `harnessStatus=COMPLETED` | Runner finished observe+Oracle+persist successfully |
| G5-3 `VALID` | Lab acceptance evaluator + history gate + exclusivity |

A trial can be Temporal-TERMINATED and harness-COMPLETED (expected G5-3 shape) yet still fail G5-3 VALID if history/eventId/preflight fails.

## IndependentOracle

1. `OracleSnapshotRepository` loads reservation/payment/shipment/stock (+ op log) for `orderId`/`sku`  
2. `OutcomeClassifier.classify` → `BusinessOutcome`  
3. No Temporal status / LLM text as classification input (`IndependenceTest`)

### Four labels (`BusinessOutcome`)

| Label | Typical meaning |
| --- | --- |
| `CONFIRMED_SUCCESS` | Success entity package consistent |
| `CONFIRMED_FAILURE` | Clean failure package (e.g. compensated F1-P/F2) |
| `AMBIGUOUS` | Insufficient / unresolved (e.g. UNKNOWN payment, missing stock evidence) |
| `INVALID` | Invariant violation in snapshot |

## ExpectedCellOutcome (harness)

Maps profile → expected classification (+ F3-Unresolved gate flag). Independent of classifier internals but aligned with protocol expectations.

## MatrixExperimentDriver

Runs a schedule of canonical profiles; aggregates `MatrixSummary`. Multi-profile schedules register full concurrent fault controller map (`CanonicalFaultProfiles.needsAllProfileRegistration`).

## Safety

- **ENV lock** — destructive ops only in dedicated lab env  
- **JDBC allowlist** — fail-closed DB targeting  
- Both checked before reset (`TrialRunner` → `environment.assertSafeForDestructiveOps()`)
