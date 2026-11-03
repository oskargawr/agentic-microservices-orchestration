# TH-400 — Evidence basis & limitation register (S4-C)

| Field | Value |
| --- | --- |
| **Status** | Documentation (D-AUTH-S4 / S4-C) |
| **Code tip base** | `main` @ S3 merge `bcc38c7` + S4 branch |
| **Does not change** | Protocol-v0 timings · Oracle four-label set · profile semantics |

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
| Matrix serial schedule + isolation | Yes (driver) | — | Yes | No |
| Expected-vs-actual cell reporting | Yes | — | Yes | No |
| Temporal `maxAttempts=5` quantitative | Policy unit + S4-C retry test | arrange exhaustion CompIT (related) | — | No |

## 3. Limitation register (carried + current)

| ID | Limitation | Origin | Disclosure |
| --- | --- | --- | --- |
| L-S3E | No live Temporal S3-E / F1–F2 lab E2E | S1–S3 / S4-D deferred | Do not claim production Temporal ops from CompIT alone |
| L-RETRY-Q | Until S4-C test: retry often behavioral only | S3 audit | S4-C adds **one** bounded quantitative check: `defaultOptions` maxAttempts=5 on exhausted charge |
| L-503-NEST | Nested unrelated `HTTP_503` in exotic cause chain | S3 residual | Theoretical; no classification framework |
| L-PROBE | Preflight probe residue / forge-URL class | S1/S2 | StateReset required; forged base URL can false-positive verify |
| L-CLI-ORACLE | Historical: CLI did not gate on expected Oracle | S1/S2 | **Mitigated in S4-B** via matrix expected-vs-actual + exit codes |
| L-SHIP-NR | Broad `noRollbackFor` on arrange fault path | S2 | Disclose in thesis Methods |
| L-EXP | No EXP-* pilot dataset yet | EXPERIMENT_LOG | Profiles ≠ completed experiment campaign |
| L-AXC | No Variant C / A×C comparison | MVP deferral | OD-FC preserves extension; C not in S4 |

## 4. Quantitative retry policy under measurement (S4-C)

| Item | Value |
| --- | --- |
| Policy | `ActivityPolicies.defaultOptions()` forward retries |
| `maximumAttempts` (forward) | **5** |
| Recovery stub | `lostIdRecoveryOptions()` `maximumAttempts=1` (D-208 B) |
| Observable | `RecordingActivities.chargeInvocations()` under `TestWorkflowEnvironment` |
| Test | `…s4c_defaultOptions_exhaustedRetryableCharge_attemptsEqualForwardMaxPlusRecovery` |
| Exhausted behavior | **6** charge executions (5 forward + 1 recovery); not SUCCESS/consume |
| Non-goal | Generic retry observability framework; live-cluster attempt accounting |

Independent: UNKNOWN resolve **loop** bound (`resolveAttemptLimit`, default 3) remains separate from Temporal activity retries (D-202 / TH-202).

## 5. Nested HTTP_503 residual

`isExhaustedTransportOpacityFailure` accepts any cause-chain `ApplicationFailure` type `HTTP_503` when hint is `RESOLVE_TRANSPORT_OPAQUE`. An exotic nested unrelated 503 remains a **theoretical** residual — documented, not generalized.
