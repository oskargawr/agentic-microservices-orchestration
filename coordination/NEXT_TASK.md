# NEXT_TASK

**Status:** `proposed` — awaiting Owner approval  
**Last updated:** 2026-11-01

> **TH-102 completed** on `feature/th-102-inventory-service`. Do not start TH-103 until Owner sets `approved`.

## Task ID and title

**TH-103** — Payment simulation (charge / refund / UNKNOWN)

## Objective

Implement Payment service with Postgres persistence and HTTP APIs per `openapi-payment.yaml`: charge outcomes including `UNKNOWN`, resolve probes, refund only from `CAPTURED`, idempotency header semantics, query snapshot.

## Why it matters

Second shared service for A/C; enables F3 ambiguous-payment fault and compensation refund path.

## Acceptance criteria (from backlog)

- States Paid/Failed/UNKNOWN + query (map to CAPTURED/FAILED/UNKNOWN)
- Align with TH-101.1 resolve / refund rules

## Dependencies

- TH-101, TH-101.1, TH-102 (**done**)

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-01 | proposed | Cursor (after TH-102) |
