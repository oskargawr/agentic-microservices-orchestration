# HANDOFF_TO_CHATGPT

**Date:** 2026-11-01  
**Task:** TH-101.1 — Contract clarification and OpenAPI hardening  
**Prepared by:** Cursor Agent  
**MODE:** IMPLEMENTATION  
**Branch:** `feature/th-101.1-contract-hardening`

---

## Exact changes

1. **Idempotency** — `Idempotency-Key` header only; body fields removed from DTOs. Same key+payload → replay; same key+different payload → `IDEMPOTENCY_KEY_CONFLICT` (`IdempotencyRules`). Recommended key values in `IdempotencyKeys` (single-SKU MVP).
2. **Payment resolve** — `PaymentResolveRules` + `ResolveProbeOutcome`: confirmed capture → CAPTURED; authoritative non-capture → FAILED; inconclusive keeps UNKNOWN/PENDING; contradiction check; no refund without CAPTURED.
3. **OpenAPI** — split into per-service specs + shared `components.yaml` (correct server per service). Removed monolithic `openapi.yaml`.
4. **Errors / validation** — `ErrorCode` enum; `ApiError.code` typed; charge `amountMinor > 0`.
5. **Consume** — documented C-101-1: orchestration after confirmed arrange; atomic once-only stock update; RELEASED/CONSUMED terminal.

Owner C-101-1..3 recorded as **confirmed** in D-014; D-015 for hardening.

---

## Spec layout

| File | Server |
| --- | --- |
| `docs/contracts/components.yaml` | shared schemas |
| `docs/contracts/openapi-inventory.yaml` | `:8081` |
| `docs/contracts/openapi-payment.yaml` | `:8082` |
| `docs/contracts/openapi-shipping.yaml` | `:8083` |
| `docs/contracts/redocly.yaml` | lint config |
| `docs/contracts/STATE_MODELS.md` | semantics |
| `docs/contracts/README.md` | index |

---

## Idempotency & resolve (summary)

| Topic | Rule |
| --- | --- |
| Transport | Header `Idempotency-Key` |
| Conflict | 409 `IDEMPOTENCY_KEY_CONFLICT` |
| Resolve inconclusive | preserve UNKNOWN/PENDING |
| Timeout | ≠ FAILED |
| Refund | CAPTURED only |

---

## Tests & validation

```text
./gradlew :contracts:test build  → BUILD SUCCESSFUL
npx @redocly/cli@1.25.0 lint --config docs/contracts/redocly.yaml
  → Woohoo! Your API descriptions are valid. (inventory + payment + shipping)
```


No Temporal / LLM / persistence / TH-102 HTTP.

---

## Unresolved

None blocking TH-102. Optional later: exact MVP charge amountMinor value (TH-103).

---

## Recommended next

**TH-102** Inventory — Owner must set `NEXT_TASK` to `approved`.
