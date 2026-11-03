# TH-400 — Evidence basis & limitation register (S4-C)

| Field | Value |
| --- | --- |
| **Status** | Documentation (D-AUTH-S4 / S4-C + PR #15 review remediation) |
| **Code tip base** | `main` @ S3 merge `bcc38c7` + S4 branch `feature/th-400-s4-matrix-readiness` |
| **Does not change** | Protocol-v0 timings · Oracle four-label set · profile semantics · D-AUTH-S4 |

## 1. Evidence correlation (artifact identity)

Every durable trial JSON should allow reconstruction of:

| Field | Source |
| --- | --- |
| `experimentId` / `trialId` / `faultProfileId` / `repetitionIndex` | `TrialRecord` / `TrialSpec` |
| `protocolId` | `ExperimentDefinition` (pilot: `protocol-v0`) |
| `gitSha` | `ExperimentDefinition.gitSha` / CLI `--git-sha` |
| `effectiveConfig` | `ArmedFaultRecord` on the trial |
| Oracle classification / pattern / invariants | `OracleSummary` (DB-primary IndependentOracle) |
| Matrix expected-vs-actual | `matrix-summary.json` (harness layer; **not** Oracle) |

`EXPERIMENT_LOG.md` remains the index of **EXP-*** runs; it stays empty until a real pilot archive is produced.

## 2. What each test layer supports

| Claim class | Unit | CompIT (`TestWorkflowEnvironment` + Boot + Postgres) | Mocked runner lifecycle | Live Temporal cluster |
| --- | --- | --- | --- | --- |
| Profile arm/verify/disarm routing | Yes | — | Yes | No (S4-D deferred) |
| Service header injection semantics | Payment/Shipping IT | Variant A CompIT | — | No |
| Saga compensation / leave-dangling | — | Yes | — | No |
| Matrix serial schedule + isolation | Yes (driver) | — | Yes (incl. lifecycle event order) | No |
| Expected-vs-actual cell reporting | Yes (`MatrixSummaryTest`) | — | Yes | No |
| Planned vs executed cell accounting | Yes | — | Abort / exclusion paths | No |
| Temporal forward `maximumAttempts=5` + recovery `1` | Policy unit + S4-C CompIT counters | arrange exhaustion CompIT (related) | — | **No** (not claimed) |
| Schedule-aware CLI controller URLs | Yes (`RunnerMainFaultControllerTest`) | — | — | No |

**Live Temporal cluster tests were not performed** (S4-D not authorized).

## 3. Limitation register (carried + current)

| ID | Limitation | Origin | Disclosure |
| --- | --- | --- | --- |
| L-S3E | No live Temporal S3-E / F1–F2 lab E2E | S1–S3 / S4-D deferred | Do not claim production Temporal ops from CompIT alone |
| L-RETRY-Q | Retry often behavioral only | S3 audit | **Mitigated (bounded):** S4-C separates forward charge (5) vs recovery (1) under `TestWorkflowEnvironment` |
| L-503-NEST | Nested unrelated `HTTP_503` in exotic cause chain | S3 residual | Theoretical; no classification framework |
| L-PROBE | Preflight probe residue / forge-URL class | S1/S2 | StateReset required; forged base URL can false-positive verify |
| L-CLI-ORACLE | Historical: CLI did not gate on expected Oracle | S1/S2 | **Mitigated in S4-B** via matrix expected-vs-actual + exit codes |
| L-SHIP-NR | Broad `noRollbackFor` on arrange fault path | S2 | Disclose in thesis Methods |
| L-EXP | No EXP-* pilot dataset yet | EXPERIMENT_LOG | Profiles ≠ completed experiment campaign |
| L-AXC | No Variant C / A×C comparison | MVP deferral | OD-FC preserves extension; C not in S4 |

## 4. Quantitative retry policy under measurement (S4-C)

| Item | Value |
| --- | --- |
| Forward policy | `ActivityPolicies.defaultOptions()` |
| Forward `maximumAttempts` | **5** = up to **five total** activity executions (not five retries after the first attempt) |
| Recovery policy | `ActivityPolicies.lostIdRecoveryOptions()` |
| Recovery `maximumAttempts` | **1** = a single recovery execution (separate activity invocation) |
| Instrumentation | `RecordingActivities.forwardChargeInvocations()` / `recoveryChargeInvocations()` |
| Test | `OrderFulfillmentRetryTimeoutTest.s4c_defaultOptions_exhaustedRetryableCharge_forwardFive_recoveryOne` |
| Exhausted behavior | forward **5** + recovery **1**; not SUCCESS; no `consume` |
| Evidence scope | **`TestWorkflowEnvironment` only** — not a live-cluster measurement |
| Non-goal | Generic retry observability framework; production/live attempt accounting |

Independent: UNKNOWN resolve **loop** bound (`resolveAttemptLimit`, default 3) remains separate from Temporal activity retries (D-202 / TH-202). BUSINESS non-retry remains covered by `t202_3_businessFailureIsNotRetried_attemptCountIsOne`.

## 5. Matrix summary accounting (PR #15 remediation)

| Field | Meaning |
| --- | --- |
| `plannedCells` | `faultProfileIds().size() * n` from `ExperimentDefinition` |
| `executedCells` | Observed `TrialRecord` count (never synthesize missing successes) |
| `matched` / `mismatched` / `excluded` / `notExecuted` | Separate counters |
| `allMatched` | False if any planned cell is missing, excluded, or mismatched |

## 6. Nested HTTP_503 residual

`isExhaustedTransportOpacityFailure` accepts any cause-chain `ApplicationFailure` type `HTTP_503` when hint is `RESOLVE_TRANSPORT_OPAQUE`. An exotic nested unrelated 503 remains a **theoretical** residual — documented, not generalized.
