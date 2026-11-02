# TH-206 — Temporal E2E in CI — no silent skip

| Field | Value |
| --- | --- |
| **ID** | TH-206 |
| **Title** | Temporal E2E in CI — no silent skip |
| **Estimate** | 1–2 SP |
| **Status** | **Done / Closed** (PR #7 merge `c1caa5b`; post-merge verify OK 2026-11-02) |
| **Depends** | TH-205 (Done) |
| **Tag** | CORE / CI hardening |
| **Owner approved** | 2026-11-02 |

## Focus

Enforce execution of `ConsoleRealTemporalE2EIT` in CI:

- Fail hard if Temporal or Postgres is unavailable when the test is required.
- Fail hard if the test is disabled or skipped in CI.
- Do **not** duplicate existing Docker Compose startup in `.github/workflows/ci.yml`.
- Keep local optional-Postgres / optional-Temporal `@EnabledIf` behavior for **non-CI** development.

## Implementation sketch

1. `ConsoleTemporalE2EGate` — CI/`THESIS_REQUIRE_TEMPORAL_E2E` → fail-hard; local → optional skip.
2. CI env `THESIS_REQUIRE_TEMPORAL_E2E=true` (plus default `CI=true`).
3. `scripts/ci-assert-console-temporal-e2e.sh` — assert JUnit XML `tests>=1` and `skipped=0`.
