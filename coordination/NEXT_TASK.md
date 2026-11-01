# NEXT_TASK

**Status:** `in_progress` — implementation complete; awaiting PR merge  
**Last updated:** 2026-11-01

## Task ID and title

**TH-104** — Shipping service (arrange / cancel)

## Objective

Implement Shipping with Postgres persistence and HTTP APIs per `openapi-shipping.yaml`: arrange, cancel, snapshot, idempotency, concurrency-safe cancellation.

## Acceptance criteria

- Taiga: tests + compensation cancel
- Owner brief: Flyway schema `shipping`, port 8083, real Postgres integration tests

## Implementation status

- Schema, HTTP, idempotency, concurrency docs, 7/7 Postgres tests green
- OpenAPI lint green
- PR to be opened against `main`

## Precondition verified

- TH-103 merged to `main` (PR #2, `5bdf809`); payment verification checks OK

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-01 | proposed | Cursor |
| 2026-11-01 | approved | Owner (TH-104 preparation brief) |
| 2026-11-01 | in_progress | Cursor |
