# TH-201 — Closure record

**Status:** **Done / Closed**  
**Closed:** 2026-11-02  
**Do not reopen TH-201 scope.**

## Merge

| Field | Value |
| --- | --- |
| PR | [#6](https://github.com/oskargawr/agentic-microservices-orchestration/pull/6) |
| Merge commit | `4814de6` (`4814de6f1a37db27b265e03314b6e23a7e18f0e3`) |
| Implementation commit | `f052768` (`f052768c2e751397a11a8795728a52d02dc7d7ba`) |
| Base before merge | `main` @ `1137fd1` |

## Decisions

- **D-201** remains **approved** (U-201-1…U-201-5 + clarifications). Unchanged at closure.

## Post-merge verification (local, 2026-11-02)

- Local `main` fast-forwarded to `origin/main` @ `4814de6` (contains merge).
- `./gradlew cleanTest test --max-workers=1` → **BUILD SUCCESSFUL**
- Integration / Temporal tests **executed** (`skipped=0` on all suites below).

| Suite | tests | skipped | failures | errors |
| --- | --- | --- | --- | --- |
| OrderFulfillmentCompensationIT | 6 | 0 | 0 | 0 |
| OrderFulfillmentWorkflowOrderTest | 7 | 0 | 0 | 0 |
| OrderFulfillmentHappyPathIT | 1 | 0 | 0 | 0 |
| ConsoleRealTemporalE2EIT | 1 | 0 | 0 | 0 |
| InventoryIntegrationTest | 11 | 0 | 0 | 0 |
| PaymentIntegrationTest | 13 | 0 | 0 | 0 |
| ShippingIntegrationTest | 7 | 0 | 0 | 0 |
| contracts (aggregated XML) | 35 | 0 | 0 | 0 |
| **Total (reported XML)** | **81** | **0** | **0** | **0** |

Infra used: Postgres `:5433`, Temporal `:7233` (for console E2E); Compensation/HappyPath ITs use Temporal **test environment** + real service bootJars.

## Artifacts present on main

- `OrderFulfillmentWorkflowImpl` saga + `newDetachedCancellationScope`
- `BusinessOutcome` / extended result
- `OrderFulfillmentCompensationIT`
- `docs/architecture/VARIANT_A_HAPPY_PATH.md` (compensation section)

## Known follow-up (coverage limitation — not TH-201 reopen)

Dedicated **workflow cancellation** integration test (signal/cancel main scope → assert detached compensation still runs) was **not** added in TH-201. Documented as known coverage gap / future follow-up; does **not** change approved TH-201 scope or D-201.

## Explicit non-actions

- TH-206 remains **Proposed** / unapproved (no implementation).
- Protocol, invariants, oracle, experimental constants unmodified.
- Application code not modified in this closure task.
