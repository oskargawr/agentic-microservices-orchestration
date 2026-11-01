# NEXT_TASK

**Status:** `approved` — Owner brief TH-200 after TH-104 closure  
**Last updated:** 2026-11-01

## Task ID and title

**TH-200** — Variant A: Temporal happy path (reserve → charge → arrange → consume)

## Objective

Deterministic Temporal workflow using shared service HTTP APIs; no LLM, no saga/faults, no runner.

## Acceptance criteria

- Taiga: End-to-end COMPLETED (happy path)
- Workflow + activity tests; independent oracle states CONSUMED / CAPTURED / ARRANGED

## Implementation status

- Module `:orchestration:variant-a` implemented
- Tests green locally (workflow + Postgres IT)
- PR pending

## Precondition verified

- TH-104 merged to `main` (PR #3, `45849ab`); CI Postgres + service tests verified

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-01 | proposed | Cursor (after TH-104) |
| 2026-11-01 | approved | Owner (TH-200 preparation brief) |
| 2026-11-01 | in_progress | Cursor |
