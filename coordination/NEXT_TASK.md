# NEXT_TASK

**Status:** `proposed` — awaiting Owner approval  
**Last updated:** 2026-11-01

> **TH-100 completed.** Do not start TH-101 until Owner sets `approved`.

## Task ID and title

**TH-101** — Kontrakty API + model stanu zamówienia

## Objective

Define shared API contracts (OpenAPI/DTO) and the order/reservation/payment/shipment state model matching `BUSINESS_INVARIANTS_AND_ORACLE.md` (`HELD→CONSUMED`, payment `UNKNOWN`, etc.) — without full service business logic if split across TH-102..104.

## Why it matters

Locks the shared surface for A and C before implementing inventory/payment/shipping.

## Acceptance criteria (from backlog)

- OpenAPI/DTO + state diagram
- Consistent with TH-011/D-012 invariants (no double stock decrement on consume)
- No Temporal workflows / LLM / experiment runner in this ticket unless Owner expands

## Dependencies

- TH-011, TH-012, TH-100 (**done**)

## Status history

| Date | Status | By |
| --- | --- | --- |
| 2026-11-01 | proposed | Cursor (after TH-100) |
