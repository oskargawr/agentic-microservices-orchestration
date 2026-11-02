# Experiment Runner (TH-402 / TH-400 S1–S2)

Bounded harness library + CLI (`RunnerMain`). **Not** a Spring Boot HTTP service.

## Module

`:experiment:runner`

## Supported fault profiles

| Profile | Default | CLI | Injection |
| --- | --- | --- | --- |
| **F0** | yes | `--fault-profile=F0` (or omit) | none |
| **F1-P** | no | `--fault-profile=F1-P` (explicit) | `X-Thesis-Fault: CHARGE_FAIL` via workflow input |
| **F2** | no | `--fault-profile=F2` (explicit) | `X-Thesis-Fault: ARRANGE_REJECTED` via workflow arrange hint |

Canonical F1 id is **`F1-P` only** (do not pass `F1`).

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

## Oracle note (F1-P / F2)

Acceptance for a successful F1-P or F2 cell is Oracle classification **`CONFIRMED_FAILURE`**.  
`OraclePattern.F1` is **not** required for F1-P: `FAILED + RELEASED` may classify as pattern **`F2`** under the existing `OutcomeClassifier` order. Clean F2 (arrange reject after CAPTURED) also maps to pattern **`F2`**. Do not change the classifier for S1/S2.

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

Requires Temporal + Variant A worker + business services against the allowlisted DB. For F1-P, Payment must have injection enabled. For F2, Shipping must have injection enabled.

## Tests

```bash
./gradlew :experiment:runner:test \
  :services:payment:test --tests '*PaymentIntegrationTest.confirmedFailure' \
  :services:shipping:test --tests '*ShippingIntegrationTest.arrangeRejected*' \
  :orchestration:variant-a:test --tests '*OrderFulfillmentCompensationIT.t201_1*' \
  :orchestration:variant-a:test --tests '*OrderFulfillmentCompensationIT.t400_s2*'
```
