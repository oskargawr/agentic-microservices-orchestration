# Experiment Runner (TH-402 / TH-400 S1)

Bounded harness library + CLI (`RunnerMain`). **Not** a Spring Boot HTTP service.

## Module

`:experiment:runner`

## Supported fault profiles

| Profile | Default | CLI | Injection |
| --- | --- | --- | --- |
| **F0** | yes | `--fault-profile=F0` (or omit) | none |
| **F1-P** | no | `--fault-profile=F1-P` (explicit) | `X-Thesis-Fault: CHARGE_FAIL` via workflow input |

Canonical F1 id is **`F1-P` only** (do not pass `F1`).

## F1-P environment (fail-closed)

Before an F1-P trial starts, `FaultController.verify` must confirm that the **live** Payment process honors fault injection:

- Required: Payment started with `thesis.payment.fault-injection-enabled=true`
- Runner preflight: POST existing `/api/v1/payments/charge` with `X-Thesis-Fault: CHARGE_FAIL` and require observed state **`FAILED`**
- HTTP 2xx alone is **not** sufficient (when injection is off, Payment returns **CAPTURED**)
- CLI requires `--payment-base-url=http://127.0.0.1:8082` (or `THESIS_PAYMENT_BASE_URL`) for F1-P
- If confirmation fails → trial `EXCLUDED` / `FAULT_VERIFY_FAILED` (no workflow start)

F0 does **not** call the payment preflight.

## Oracle note (F1-P)

Acceptance for a successful F1-P cell is Oracle classification **`CONFIRMED_FAILURE`**.  
`OraclePattern.F1` is **not** required: `FAILED + RELEASED` may classify as pattern **`F2`** under the existing `OutcomeClassifier` order. Do not change the classifier for S1.

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

Requires Temporal + Variant A worker + business services against the allowlisted DB. For F1-P, Payment must have injection enabled.

## Tests

```bash
./gradlew :experiment:runner:test \
  :services:payment:test --tests '*PaymentIntegrationTest.confirmedFailure' \
  :orchestration:variant-a:test --tests '*OrderFulfillmentCompensationIT.t201_1*'
```
