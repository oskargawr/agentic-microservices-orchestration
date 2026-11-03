# Experiment Runner (TH-402 / TH-400 S1–S4)

Bounded harness library + CLI (`RunnerMain`). **Not** a Spring Boot HTTP service.

**S4-A/B:** multi-profile matrix (`MatrixExperimentDriver`), schedule-aware FaultController registration, expected-vs-actual `matrix-summary.json` (planned cells from definition × n), CLI exit codes `0/1/3`.  
**S4-D** live Temporal lab: **not** authorized.

## Module

`:experiment:runner`

## Supported fault profiles

| Profile | Default | CLI | Injection |
| --- | --- | --- | --- |
| **F0** | yes | `--fault-profile=F0` (or omit) | none |
| **F1-P** | no | `--fault-profile=F1-P` (explicit) | `X-Thesis-Fault: CHARGE_FAIL` via workflow input |
| **F2** | no | `--fault-profile=F2` (explicit) | `X-Thesis-Fault: ARRANGE_REJECTED` via workflow arrange hint |
| **F3-Recovered** | no | `--fault-profile=F3-Recovered` | charge `F3_CAPTURE_LOST_RESPONSE`; resolve/GET normal |
| **F3-Unresolved** | no | `--fault-profile=F3-Unresolved` | charge `F3_CAPTURE_LOST_RESPONSE` + resolve/GET `RESOLVE_TRANSPORT_OPAQUE` (HTTP 503) |

Canonical ids only (`F1-P`, `F3-Recovered`, `F3-Unresolved` — do not pass `F1` / `F3`).

## F1-P environment (fail-closed)

Before an F1-P trial starts, `FaultController.verify` must confirm that the **live** Payment process honors fault injection:

- Required: Payment started with `thesis.payment.fault-injection-enabled=true`
- Runner preflight: POST existing `/api/v1/payments/charge` with `X-Thesis-Fault: CHARGE_FAIL` and require observed state **`FAILED`**
- HTTP 2xx alone is **not** sufficient (when injection is off, Payment returns **CAPTURED**)
- CLI requires `--payment-base-url=http://127.0.0.1:8082` (or `THESIS_PAYMENT_BASE_URL`) for F1-P
- If confirmation fails → trial `EXCLUDED` / `FAULT_VERIFY_FAILED` (no workflow start)

F0 does **not** call the payment preflight.

## F2 environment (fail-closed)

Before an F2 trial starts, `FaultController.verify` must confirm that the **live** Shipping process honors fault injection:

- Required: Shipping started with `thesis.shipping.fault-injection-enabled=true`
- Runner preflight: POST existing `/api/v1/shipments/arrange` with `X-Thesis-Fault: ARRANGE_REJECTED` and require **HTTP 409** with body `code=ARRANGE_REJECTED`
- A bare HTTP 409 is **not** sufficient (`IDEMPOTENCY_KEY_CONFLICT` is also 409)
- CLI requires `--shipping-base-url=http://127.0.0.1:8083` (or `THESIS_SHIPPING_BASE_URL`) for F2
- If confirmation fails → trial `EXCLUDED` / `FAULT_VERIFY_FAILED` (no workflow start)

## F3 environment (fail-closed)

Both F3 profiles require Payment with `thesis.payment.fault-injection-enabled=true` and `--payment-base-url` / `THESIS_PAYMENT_BASE_URL`.

| Profile | Preflight |
| --- | --- |
| **F3-Recovered** | F3 charge observes **UNKNOWN**; unhinted GET exposes **CAPTURED** |
| **F3-Unresolved** | Recovered checks + resolve/GET with `RESOLVE_TRANSPORT_OPAQUE` return **HTTP 503**; unhinted GET still **CAPTURED** |

Opacity is **header-presence** for the armed trial (no shared mutable retry counter). Temporal retries re-send the same hint.

### Harness acceptance (F3-Unresolved) — D-AUTH-S3-2

Oracle API/classifier **unchanged**. After Oracle evaluation, the harness gate accepts only when:

1. Trial `faultProfileId=F3-Unresolved`
2. Oracle returns **`AMBIGUOUS`**
3. Snapshot: payment **CAPTURED**, single reservation **HELD**, shipment not **ARRANGED**
4. No invariant violations

Otherwise trial is `EXCLUDED` / `F3_UNRESOLVED_ACCEPTANCE_FAILED`.

F3-Recovered acceptance is Oracle **`CONFIRMED_SUCCESS`** on the full success package (no extra gate).

## Oracle note (F1-P / F2)

Acceptance for a successful F1-P or F2 cell is Oracle classification **`CONFIRMED_FAILURE`**.  
`OraclePattern.F1` is **not** required for F1-P: `FAILED + RELEASED` may classify as pattern **`F2`** under the existing `OutcomeClassifier` order. Clean F2 (arrange reject after CAPTURED) also maps to pattern **`F2`**. Do not change the classifier for S1–S3.

## CLI (examples)

F0 (default):

```bash
./gradlew :experiment:runner:run --args='\
  --jdbc-url=jdbc:postgresql://localhost:5433/business \
  --allowlist=jdbc:postgresql://localhost:5433/business \
  --results-dir=experiment/results'
```

F1-P:

```bash
./gradlew :experiment:runner:run --args='\
  --jdbc-url=jdbc:postgresql://localhost:5433/business \
  --allowlist=jdbc:postgresql://localhost:5433/business \
  --fault-profile=F1-P \
  --payment-base-url=http://127.0.0.1:8082 \
  --results-dir=experiment/results'
```

F2:

```bash
./gradlew :experiment:runner:run --args='\
  --jdbc-url=jdbc:postgresql://localhost:5433/business \
  --allowlist=jdbc:postgresql://localhost:5433/business \
  --fault-profile=F2 \
  --shipping-base-url=http://127.0.0.1:8083 \
  --results-dir=experiment/results'
```

F3-Recovered:

```bash
./gradlew :experiment:runner:run --args='\
  --jdbc-url=jdbc:postgresql://localhost:5433/business \
  --allowlist=jdbc:postgresql://localhost:5433/business \
  --fault-profile=F3-Recovered \
  --payment-base-url=http://127.0.0.1:8082 \
  --results-dir=experiment/results'
```

F3-Unresolved:

```bash
./gradlew :experiment:runner:run --args='\
  --jdbc-url=jdbc:postgresql://localhost:5433/business \
  --allowlist=jdbc:postgresql://localhost:5433/business \
  --fault-profile=F3-Unresolved \
  --payment-base-url=http://127.0.0.1:8082 \
  --results-dir=experiment/results'
```

S4 matrix (serial F0→… under one env lock; writes `matrix-summary.json`):

```bash
./gradlew :experiment:runner:run --args='\
  --jdbc-url=jdbc:postgresql://localhost:5433/business \
  --allowlist=jdbc:postgresql://localhost:5433/business \
  --fault-profiles=F0,F1-P,F2,F3-Recovered,F3-Unresolved \
  --n=1 \
  --payment-base-url=http://127.0.0.1:8082 \
  --shipping-base-url=http://127.0.0.1:8083 \
  --results-dir=experiment/results \
  --git-sha=$(git rev-parse HEAD)'
```

URL requirements are **schedule-aware**:

| Schedule | Controllers registered | Required URLs |
| --- | --- | --- |
| F0 only | F0 | none |
| single F1-P / F3-* | F0 + that profile | payment only |
| single F2 | F0 + F2 | shipping only |
| multi-profile matrix | full F0–F3 concurrent map | payment **and** shipping |

Fail-closed verify for the selected profile(s) is unchanged. `MatrixSummary.plannedCells` = `|profiles| × n` even if the run aborts early (`notExecuted` counted separately).

Exit codes: `0` all planned cells matched · `1` harness exclusion / incomplete / empty · `3` classification mismatch · `130` operator abort.

Requires Temporal + Variant A worker + business services against the allowlisted DB. For F1-P/F3, Payment must have injection enabled. For F2, Shipping must have injection enabled.

Evidence / limitations: [`docs/architecture/TH_400_EVIDENCE_AND_LIMITATIONS.md`](../../docs/architecture/TH_400_EVIDENCE_AND_LIMITATIONS.md).

## G5 — Live Temporal runner pre-pilot (OWN-P4 / D-AUTH-G5)

**Status:** pre-pilot gate, **not** a merge gate. G5 is **not** S4-D and does **not** constitute an EXP-* confirmatory campaign. Spec SoT: [`coordination/TH_402_G5_DISCOVERY.md`](../../coordination/TH_402_G5_DISCOVERY.md).

| Scenario | Worker on `variant-a-order-fulfillment` | Expected Temporal | `terminatedBy` |
| --- | --- | --- | --- |
| **G5-2** | **Yes** (dedicated stack) | natural `COMPLETED` | `WORKFLOW_STOP` |
| **G5-3** | **No** (queue exclusivity preflight) | `TERMINATED` after real `T_run_max=120s` | `T_RUN_MAX` |

G5-2 and G5-3 are **independent** scenarios. A worker-down G5-2 accident is **not** valid G5-3 evidence. An unexpected worker during G5-3 yields `g5-3-acceptance.json` = `INVALID` without mutating `TrialRecord`.

### Boot order

- **G5-2:** infra (Postgres + Temporal) → business services → **worker up** → opt-in IT  
- **G5-3:** infra → business services → **no pollers** on `variant-a-order-fulfillment` → isolation preflight → opt-in IT → history export + `g5-3-acceptance.json`

### Environment

| Variable | Role |
| --- | --- |
| `THESIS_RUN_G5=true` | Opt-in gate (required) |
| `THESIS_RUN_G5_SCENARIO` | `G5-2` · `G5-3` · `ALL` (default) |
| `THESIS_G5_DEDICATED_ENV=true` | Operator **ack** of a dedicated Temporal lab (required, but **not** poller proof) |
| `TEMPORAL_TARGET` / `TEMPORAL_NAMESPACE` | Temporal client |
| `VARIANT_A_TEST_JDBC_URL` (+ user/password) | Allowlisted business DB |
| `GIT_SHA` | Recorded on trial / acceptance |

**Not evidence:** `THESIS_G5_DEDICATED_ENV` alone, or any env-flag poller override. Live G5-3 exclusivity uses Temporal **`DescribeTaskQueue`** (workflow + activity, `reportPollers=true`). API failure ⇒ preflight **FAIL** (fail closed). History `eventId` gate remains a separate post-trial check.

JDBC allowlist must include the same URL used by StateReset / Independent Oracle. Protocol timings are **unmodified** (`T_run_max=120s`, `T_observe=30s`). Ordinary CI must **not** set `THESIS_RUN_G5` (no long-running G5 job).

### G5-3 queue exclusivity checklist

1. Operator ack: `THESIS_G5_DEDICATED_ENV=true` (dedicated lab deployment).  
2. Stop console worker, `:orchestration:worker`, embedded factories, leftover IT workers.  
3. Preflight: `DescribeTaskQueue` reports **zero** pollers on `variant-a-order-fulfillment` (workflow + activity); otherwise **FAIL** before `TrialRunner.run`.  
4. Paginated history for exact `workflowId`/`runId` (`nextPageToken` until empty); incomplete retrieval ⇒ acceptance `INVALID`.  
5. Gate: `EVENT_TYPE_WORKFLOW_TASK_STARTED` with `eventId` **before** `EVENT_TYPE_WORKFLOW_EXECUTION_TERMINATED` ⇒ `INVALID`.  
6. Write `…/acceptance/g5-3-acceptance.json` **without** rewriting `TrialRecord`, then **fail the test** if acceptance ≠ `VALID`.

### Local commands (execution requires **separate** Owner authorization)

Default Gradle/`test` runs **must not** set `THESIS_RUN_G5` (class disabled; no Docker/worker start).

```bash
# Infra
cd infra && docker compose up -d && cd ..
# Build service bootJars once
./gradlew :services:inventory:bootJar :services:payment:bootJar :services:shipping:bootJar

export THESIS_RUN_G5=true
export THESIS_G5_DEDICATED_ENV=true
export TEMPORAL_TARGET=localhost:7233
export VARIANT_A_TEST_JDBC_URL=jdbc:postgresql://localhost:5433/business
export GIT_SHA=$(git rev-parse HEAD)

# Independently selectable:
export THESIS_RUN_G5_SCENARIO=G5-2   # or G5-3 or ALL
./gradlew :experiment:runner:test --tests '*RunnerLiveTemporalG5IT*'
```

Approximate wall clock: G5-2 ~40–90s; G5-3 ~160–200s. Retain trial JSON, history export, and acceptance artifact; distinguish any post-evidence cleanup terminate (`g5-cleanup`) from measured R6 (`T_run_max`).

## Tests

```bash
# Ordinary non-live runner tests (G5 live IT stays disabled without THESIS_RUN_G5)
./gradlew :experiment:runner:test \
  :services:payment:test \
  :orchestration:variant-a:test --tests '*OrderFulfillmentCompensationIT.f3*' \
  :orchestration:variant-a:test --tests '*OrderFulfillmentCompensationIT.t201_*' \
  :orchestration:variant-a:test --tests '*OrderFulfillmentCompensationIT.t400_s2*'
```
