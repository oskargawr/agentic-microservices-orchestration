# TH-206 — Proposed backlog item

| Field | Value |
| --- | --- |
| **ID** | TH-206 |
| **Title** | Temporal E2E in CI — no silent skip |
| **Estimate** | 1–2 SP |
| **Status** | **Proposed** (not approved for implementation) |
| **Depends** | TH-205 (Done) |
| **Tag** | CORE / CI hardening |

## Focus

Enforce execution of `ConsoleRealTemporalE2EIT` in CI:

- Fail hard if Temporal or Postgres is unavailable when the test is required.
- Fail hard if the test is disabled or skipped in CI.
- Do **not** duplicate existing Docker Compose startup in `.github/workflows/ci.yml` (Compose already brings up Postgres + Temporal).
- Keep local optional-Postgres / optional-Temporal `@EnabledIf` behavior for **non-CI** development.

## Non-goals

- Re-implement TH-205 console features
- Change service integration-test intent
- Expand experiment protocol or oracle

## Acceptance sketch (proposed)

1. On `CI=true` (or equivalent), `ConsoleRealTemporalE2EIT` runs and JUnit shows `skipped=0`.
2. Missing Temporal/Postgres in CI → job failure (not skip).
3. Existing inventory/payment/shipping ITs remain usable locally when Postgres is absent.
